package ro.uvt.fsgc.orar.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.SavedTimetable;
import ro.uvt.fsgc.orar.domain.SavedTimetableEntry;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.SavedTimetableRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

/**
 * History of timetables. Solver jobs live only in memory and {@code scheduled_activity} holds a
 * single current assignment, so a snapshot is the only way to keep a good result across a
 * regeneration. Saving copies the current placement; restoring writes it back.
 */
@RestController
@RequestMapping("/api/saved-timetables")
@CrossOrigin
public class SavedTimetableController {

    private final SavedTimetableRepository savedRepo;
    private final ScheduledActivityRepository activityRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final RoomRepository roomRepo;

    public SavedTimetableController(SavedTimetableRepository savedRepo,
                                    ScheduledActivityRepository activityRepo,
                                    TimeSlotRepository timeSlotRepo, RoomRepository roomRepo) {
        this.savedRepo = savedRepo;
        this.activityRepo = activityRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.roomRepo = roomRepo;
    }

    static class ConflictException extends RuntimeException {
        ConflictException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, String>> onConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    /** List row: counts only, never the (potentially hundreds of) entries. */
    public record SavedSummary(Long id, String name, String note, String createdAt, String score,
                               int totalActivities, int assignedCount) {
    }

    private static SavedSummary summary(SavedTimetable s) {
        return new SavedSummary(s.getId(), s.getName(), s.getNote(), s.getCreatedAt().toString(),
                s.getScore(), s.getTotalActivities(), s.getAssignedCount());
    }

    @GetMapping
    public List<SavedSummary> list() {
        return savedRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(SavedTimetableController::summary)
                .toList();
    }

    public record SaveReq(String name, String note, String score) {
    }

    /** Snapshots the current placement of every activity. */
    @PostMapping
    @Transactional
    public SavedSummary save(@RequestBody SaveReq req) {
        List<ScheduledActivity> activities = activityRepo.findAll();
        if (activities.isEmpty()) {
            throw new ConflictException("Nu există activități de salvat. Importă datele întâi.");
        }
        String name = req.name() == null || req.name().isBlank()
                ? "Orar " + LocalDateTime.now().withNano(0) : req.name().trim();

        SavedTimetable snapshot = new SavedTimetable();
        snapshot.setName(name);
        snapshot.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        snapshot.setScore(req.score() == null || req.score().isBlank() ? null : req.score().trim());
        snapshot.setCreatedAt(LocalDateTime.now().withNano(0));
        snapshot.setTotalActivities(activities.size());
        snapshot.setAssignedCount((int) activities.stream()
                .filter(ScheduledActivity::isPlaced).count());

        for (ScheduledActivity a : activities) {
            snapshot.getEntries().add(new SavedTimetableEntry(snapshot, a.getId(),
                    a.getTimeSlot() == null ? null : a.getTimeSlot().getId(),
                    a.getRoom() == null ? null : a.getRoom().getId()));
        }
        return summary(savedRepo.save(snapshot));
    }

    public record RenameReq(String name, String note) {
    }

    @PutMapping("/{id}")
    @Transactional
    public SavedSummary rename(@PathVariable Long id, @RequestBody RenameReq req) {
        SavedTimetable s = savedRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Orarul salvat nu există (id " + id + ")"));
        if (req.name() != null && !req.name().isBlank()) {
            s.setName(req.name().trim());
        }
        s.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        return summary(savedRepo.save(s));
    }

    /** What a restore actually managed to apply. */
    public record RestoreResult(int applied, int skippedMissingActivity, int skippedMissingSlotOrRoom,
                                int cleared) {
    }

    /**
     * Writes a snapshot back onto the live activities. Entries whose activity, slot or room no
     * longer exist are skipped rather than failing, and the counts say what happened.
     */
    @PostMapping("/{id}/restore")
    @Transactional
    public RestoreResult restore(@PathVariable Long id) {
        SavedTimetable s = savedRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Orarul salvat nu există (id " + id + ")"));

        int applied = 0;
        int missingActivity = 0;
        int missingRef = 0;
        int cleared = 0;

        for (SavedTimetableEntry e : s.getEntries()) {
            Optional<ScheduledActivity> found = activityRepo.findById(e.getActivityId());
            if (found.isEmpty()) {
                missingActivity++;
                continue;
            }
            ScheduledActivity a = found.get();
            if (e.getTimeSlotId() == null || e.getRoomId() == null) {
                a.setTimeSlot(null);
                a.setRoom(null);
                activityRepo.save(a);
                cleared++;
                continue;
            }
            Optional<TimeSlot> ts = timeSlotRepo.findById(e.getTimeSlotId());
            Optional<Room> room = roomRepo.findById(e.getRoomId());
            if (ts.isEmpty() || room.isEmpty()) {
                missingRef++;
                continue;
            }
            a.setTimeSlot(ts.get());
            a.setRoom(room.get());
            activityRepo.save(a);
            applied++;
        }
        return new RestoreResult(applied, missingActivity, missingRef, cleared);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        SavedTimetable s = savedRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Orarul salvat nu există (id " + id + ")"));
        savedRepo.delete(s);
        return ResponseEntity.noContent().build();
    }
}
