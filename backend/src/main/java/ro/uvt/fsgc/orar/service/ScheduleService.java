package ro.uvt.fsgc.orar.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialBlockRule;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.dto.ActivityView;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.SpecialBlockRuleRepository;
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
    private final SpecialBlockRuleRepository specialBlockRepo;

    public ScheduleService(ScheduledActivityRepository activityRepo, TimeSlotRepository timeSlotRepo,
                           RoomRepository roomRepo, SpecialBlockRuleRepository specialBlockRepo) {
        this.activityRepo = activityRepo;
        this.roomRepo = roomRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.specialBlockRepo = specialBlockRepo;
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
        // an online hour never takes a room, whatever the grid sent along with the drop
        activity.setRoom(activity.isOnline() ? null : room);
        if (!activity.isPlaced()) {
            activity.setPinned(false); // an hour taken off the grid cannot stay fixed to it
        }
        activityRepo.save(activity);

        return new MoveResult(ActivityMapper.toView(activity), validate(activity));
    }

    /** How much was cleared, so the UI can say it plainly. */
    public record ClearResult(int cleared, int total) {
    }

    /**
     * Takes every hour off the grid so the timetable can be built again from nothing. Only the
     * placement goes: the activities themselves stay, and so do the rules, the weights, the rooms
     * and their unavailabilities — clearing is a fresh start for the puzzle, not for the data.
     * Pins go too, since an hour with no place cannot stay fixed to one.
     */
    @Transactional
    public ClearResult clearSchedule() {
        List<ScheduledActivity> all = activityRepo.findAll();
        int cleared = 0;
        for (ScheduledActivity a : all) {
            if (a.getTimeSlot() == null && a.getRoom() == null && !a.isPinned()) {
                continue;
            }
            a.setTimeSlot(null);
            a.setRoom(null);
            a.setPinned(false);
            cleared++;
        }
        activityRepo.saveAll(all);
        return new ClearResult(cleared, all.size());
    }

    /**
     * Fixes an activity where it stands, or releases it. Only an hour that already has a slot and
     * a room can be pinned: pinning an unplaced one would tell the solver to leave it unplaced
     * for good. Returns the same view + violations a move does, so the UI can warn when someone
     * pins an hour that already breaks a rule — the next generation will build around it anyway.
     */
    @Transactional
    public MoveResult setPinned(Long activityId, boolean pinned) {
        ScheduledActivity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
        if (pinned && !activity.isPlaced()) {
            throw new IllegalStateException("Ora trebuie întâi pusă în orar, apoi poate fi fixată.");
        }
        activity.setPinned(pinned);
        activityRepo.save(activity);
        return new MoveResult(ActivityMapper.toView(activity), validate(activity));
    }

    /** Checks the key hard constraints for a single placed activity against the whole schedule. */
    @Transactional(readOnly = true)
    public List<String> validate(ScheduledActivity activity) {
        List<String> violations = new ArrayList<>();
        TimeSlot ts = activity.getTimeSlot();
        Room room = activity.getRoom();
        if (!activity.isPlaced()) {
            return violations; // unplaced: nothing to check
        }

        // Room rules only exist when there is a room: an online hour is held nowhere.
        if (room != null) {
            if (activity.totalStudentCount() > room.getCapacity()) {
                violations.add("Room capacity exceeded (" + activity.totalStudentCount()
                        + " > " + room.getCapacity() + ")");
            }
            // Same rule the solver uses: rooms are free unless an unavailability window overlaps.
            room.getUnavailabilities().stream()
                    .filter(un -> un.overlaps(ts.getDayOfWeek(), ts.getStartTime(), ts.getEndTime()))
                    .findFirst()
                    .ifPresent(un -> violations.add("Sala este indisponibilă în acest interval ("
                            + un.getStartTime() + "–" + un.getEndTime()
                            + (un.getReason() == null ? "" : ", " + un.getReason()) + ")"));
        }
        if (activity.isMaster() && !ts.isEveningModule()) {
            violations.add("Master activity outside evening modules (6-8)");
        }
        // A reserved interval takes nothing but its own category — same rule the solver enforces.
        for (SpecialBlockRule rule : specialBlockRepo.findAll()) {
            if (rule.getTimeSlot() != null && rule.getTimeSlot().getId().equals(ts.getId())
                    && activity.getSpecialCategory() != rule.getCategory()
                    && blockAudienceMatches(activity, rule)) {
                violations.add("Interval blocat pentru " + rule.getCategory()
                        + " — aici nu se poate programa nimic");
                break;
            }
        }
        if (activity.isRequiresAmphitheater() && room != null
                && room.getTypology() != ro.uvt.fsgc.orar.domain.RoomTypology.AMPHITHEATER) {
            violations.add("Amphitheater required");
        }
        if (activity.isRequiresLab() && room != null
                && room.getTypology() != ro.uvt.fsgc.orar.domain.RoomTypology.LAB) {
            violations.add("Activitatea trebuie ținută într-un laborator");
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
            if (room != null && other.getRoom() != null
                    && other.getRoom().getId().equals(room.getId())) {
                violations.add("Room clash with " + other.getSubject().getCode());
            }
            if (!Collections.disjoint(activity.getStudentGroups(), other.getStudentGroups())) {
                violations.add("Student group clash with " + other.getSubject().getCode());
            }
        }
        return violations;
    }

    /** Same audience test the solver uses: an explicit group, or a specialization + year. */
    private static boolean blockAudienceMatches(ScheduledActivity a, SpecialBlockRule rule) {
        if (rule.getStudentGroup() != null) {
            return a.getStudentGroups().contains(rule.getStudentGroup());
        }
        if (rule.getSpecialization() != null && rule.getYear() != null) {
            return a.getStudentGroups().stream().anyMatch(g ->
                    rule.getSpecialization().equalsIgnoreCase(g.getSpecialization())
                            && rule.getYear() == g.getYear());
        }
        return false;
    }
}
