package ro.uvt.fsgc.orar.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.dto.ActivityView;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

/**
 * Reads the currently persisted timetable and applies manual edits (drag-and-drop). A move is
 * always applied (the user can force it), but the hard constraints it would break are returned so
 * the UI can warn and mark the cell as a manual override.
 */
@Service
public class ScheduleService {

    private final ScheduledActivityRepository activityRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final RoomRepository roomRepo;

    public ScheduleService(ScheduledActivityRepository activityRepo, TimeSlotRepository timeSlotRepo,
                           RoomRepository roomRepo) {
        this.activityRepo = activityRepo;
        this.roomRepo = roomRepo;
        this.timeSlotRepo = timeSlotRepo;
    }

    @Transactional(readOnly = true)
    public List<ActivityView> currentSchedule() {
        return activityRepo.findAll().stream().map(ActivityMapper::toView).toList();
    }

    /** Result of a manual move: the new view + any hard constraints it violates. */
    public record MoveResult(ActivityView activity, List<String> violations) {
    }

    @Transactional
    public MoveResult move(Long activityId, Long timeSlotId, Long roomId) {
        ScheduledActivity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
        TimeSlot ts = timeSlotId == null ? null
                : timeSlotRepo.findById(timeSlotId).orElseThrow(() ->
                new IllegalArgumentException("Time slot not found: " + timeSlotId));
        Room room = roomId == null ? null
                : roomRepo.findById(roomId).orElseThrow(() ->
                new IllegalArgumentException("Room not found: " + roomId));

        activity.setTimeSlot(ts);
        activity.setRoom(room);
        activityRepo.save(activity);

        return new MoveResult(ActivityMapper.toView(activity), validate(activity));
    }

    /** Checks the key hard constraints for a single placed activity against the whole schedule. */
    @Transactional(readOnly = true)
    public List<String> validate(ScheduledActivity activity) {
        List<String> violations = new ArrayList<>();
        TimeSlot ts = activity.getTimeSlot();
        Room room = activity.getRoom();
        if (ts == null || room == null) {
            return violations; // unplaced: nothing to check
        }

        if (activity.totalStudentCount() > room.getCapacity()) {
            violations.add("Room capacity exceeded (" + activity.totalStudentCount()
                    + " > " + room.getCapacity() + ")");
        }
        boolean covered = room.getAvailabilities().stream().anyMatch(av ->
                av.covers(ts.getDayOfWeek(), ts.getStartTime(), ts.getEndTime()));
        if (!covered) {
            violations.add("Room not available in this slot");
        }
        if (activity.isMaster() && !ts.isEveningModule()) {
            violations.add("Master activity outside evening modules (6-8)");
        }
        if (activity.isRequiresAmphitheater()
                && room.getTypology() != ro.uvt.fsgc.orar.domain.RoomTypology.AMPHITHEATER) {
            violations.add("Amphitheater required");
        }

        for (ScheduledActivity other : activityRepo.findAll()) {
            if (other.getId().equals(activity.getId()) || other.getTimeSlot() == null) {
                continue;
            }
            if (!other.getTimeSlot().getId().equals(ts.getId()) || !activity.parityClashesWith(other)) {
                continue;
            }
            if (activity.getProfessor() != null && activity.getProfessor().equals(other.getProfessor())) {
                violations.add("Professor clash with " + other.getSubject().getCode());
            }
            if (other.getRoom() != null && other.getRoom().getId().equals(room.getId())) {
                violations.add("Room clash with " + other.getSubject().getCode());
            }
            if (!Collections.disjoint(activity.getStudentGroups(), other.getStudentGroups())) {
                violations.add("Student group clash with " + other.getSubject().getCode());
            }
        }
        return violations;
    }
}
