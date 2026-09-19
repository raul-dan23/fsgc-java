package ro.uvt.fsgc.orar.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;
import ro.uvt.fsgc.orar.domain.RestrictionType;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialBlockRule;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.dto.ActivityView;
import ro.uvt.fsgc.orar.repository.ProfessorRoomRestrictionRepository;
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
    private final ProfessorRoomRestrictionRepository profRoomRepo;

    public ScheduleService(ScheduledActivityRepository activityRepo, TimeSlotRepository timeSlotRepo,
                           RoomRepository roomRepo, SpecialBlockRuleRepository specialBlockRepo,
                           ProfessorRoomRestrictionRepository profRoomRepo) {
        this.activityRepo = activityRepo;
        this.roomRepo = roomRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.specialBlockRepo = specialBlockRepo;
        this.profRoomRepo = profRoomRepo;
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
        // Dropped on a day + module without naming a room (the grid on sections has no room
        // column), so one is chosen here. Without it the hour would keep its slot, stay
        // room-less, and therefore count as unplaced — it would vanish from the grid again.
        if (room == null && ts != null && !activity.isOnline()) {
            room = pickRoom(activity, ts);
        }
        // an online hour never takes a room, whatever the grid sent along with the drop
        activity.setRoom(activity.isOnline() ? null : room);
        if (!activity.isPlaced()) {
            activity.setPinned(false); // an hour taken off the grid cannot stay fixed to it
        }
        activityRepo.save(activity);

        return new MoveResult(ActivityMapper.toView(activity), validate(activity));
    }

    /** A room offered for an hour, and why it cannot be used when it cannot. */
    public record RoomOption(Long id, String name, int capacity, String typology,
                             boolean usable, String reason) {
    }

    /**
     * Every room, said plainly: which ones this hour can actually use at that time and, for the
     * rest, what stands in the way. The grid offers this when someone wants to change the room
     * chosen automatically, so the choice is made from real options instead of guesswork.
     */
    @Transactional(readOnly = true)
    public List<RoomOption> roomOptions(Long activityId, Long timeSlotId) {
        ScheduledActivity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found: " + activityId));
        TimeSlot ts = timeSlotId != null
                ? timeSlotRepo.findById(timeSlotId).orElseThrow(() ->
                        new IllegalArgumentException("Time slot not found: " + timeSlotId))
                : activity.getTimeSlot();
        if (ts == null) {
            throw new IllegalStateException("Ora nu are încă interval, așa că nu are nevoie de sală.");
        }
        List<ScheduledActivity> others = othersIn(activity, ts);
        List<ProfessorRoomRestriction> restrictions = profRoomRepo.findAll();
        return roomRepo.findAll().stream()
                .sorted(Comparator.comparing(Room::getName))
                .map(r -> new RoomOption(r.getId(), r.getName(), r.getCapacity(),
                        r.getTypology() == null ? null : r.getTypology().name(),
                        whyNot(activity, r, ts, others, restrictions) == null,
                        whyNot(activity, r, ts, others, restrictions)))
                .toList();
    }

    /** Null when the room fits the hour at that time; otherwise the reason it does not. */
    private static String whyNot(ScheduledActivity a, Room r, TimeSlot ts,
                                 List<ScheduledActivity> othersInSlot,
                                 List<ProfessorRoomRestriction> restrictions) {
        if (!typeFits(a, r)) {
            return a.isRequiresLab() ? "nu e laborator" : "nu e amfiteatru";
        }
        if (r.getCapacity() < a.totalStudentCount()) {
            return "prea mică (" + r.getCapacity() + " locuri, " + a.totalStudentCount() + " studenți)";
        }
        if (r.getUnavailabilities().stream()
                .anyMatch(u -> u.overlaps(ts.getDayOfWeek(), ts.getStartTime(), ts.getEndTime()))) {
            return "indisponibilă în acest interval";
        }
        String busy = othersInSlot.stream()
                .filter(o -> o.getRoom().getId().equals(r.getId()))
                .map(o -> o.getSubject().getName())
                .findFirst().orElse(null);
        if (busy != null) {
            return "ocupată de „" + busy + "”";
        }
        String forbidden = forbiddenFor(a, r, restrictions);
        if (forbidden != null) {
            return forbidden;
        }
        return null;
    }

    /**
     * The professor's own room rules: a room ruled out for them, or — when they have a whitelist —
     * every room that is not on it. Missing this was how an hour of someone who may not teach in
     * P01 ended up in P01.
     */
    private static String forbiddenFor(ScheduledActivity a, Room r,
                                       List<ProfessorRoomRestriction> restrictions) {
        if (a.getProfessor() == null) {
            return null;
        }
        List<ProfessorRoomRestriction> mine = restrictions.stream()
                .filter(x -> x.getProfessor() != null
                        && x.getProfessor().getId().equals(a.getProfessor().getId()))
                .toList();
        boolean forbidden = mine.stream()
                .anyMatch(x -> x.getRestrictionType() == RestrictionType.FORBIDDEN
                        && x.getRoom().getId().equals(r.getId()));
        if (forbidden) {
            return "interzisă pentru " + a.getProfessor().getName();
        }
        List<ProfessorRoomRestriction> onlyThis = mine.stream()
                .filter(x -> x.getRestrictionType() == RestrictionType.ONLY_THIS)
                .toList();
        if (!onlyThis.isEmpty()
                && onlyThis.stream().noneMatch(x -> x.getRoom().getId().equals(r.getId()))) {
            return a.getProfessor().getName() + " poate preda doar în "
                    + String.join(", ", onlyThis.stream().map(x -> x.getRoom().getName()).toList());
        }
        return null;
    }

    /** The activities that would clash with this one in that slot (parity aware). */
    private List<ScheduledActivity> othersIn(ScheduledActivity activity, TimeSlot ts) {
        return activityRepo.findAll().stream()
                .filter(o -> !o.getId().equals(activity.getId()))
                .filter(o -> o.getTimeSlot() != null && o.getRoom() != null)
                .filter(o -> o.getTimeSlot().getId().equals(ts.getId()))
                .filter(activity::parityClashesWith)
                .toList();
    }

    /**
     * The room to drop an hour into when the person did not name one. Prefers a room that is
     * genuinely free — right type, big enough, not marked unavailable, nobody else in it at that
     * hour — and among those the smallest, the same way the solver avoids wasting a big room.
     * When nothing is free it still returns the least bad room rather than leaving the hour
     * room-less: the caller reports what the placement breaks, and the person decides.
     */
    private Room pickRoom(ScheduledActivity activity, TimeSlot ts) {
        List<Room> rooms = roomRepo.findAll();
        if (rooms.isEmpty()) {
            return null;
        }
        List<ScheduledActivity> others = othersIn(activity, ts);
        List<ProfessorRoomRestriction> restrictions = profRoomRepo.findAll();
        Comparator<Room> smallestFirst = Comparator.comparingInt(Room::getCapacity);

        // A room the professor may not use is not a candidate at all, not even a last resort.
        List<Room> allowed = rooms.stream()
                .filter(r -> forbiddenFor(activity, r, restrictions) == null)
                .toList();
        List<Room> candidates = allowed.isEmpty() ? rooms : allowed;
        List<Room> rightType = candidates.stream().filter(r -> typeFits(activity, r)).toList();
        if (!rightType.isEmpty()) {
            candidates = rightType;
        }
        final List<Room> pool = candidates;

        return pool.stream()
                .filter(r -> whyNot(activity, r, ts, others, restrictions) == null)
                .min(smallestFirst)
                // nothing entirely free: the roomiest that is at least not taken, then the rest
                .or(() -> pool.stream().filter(r -> free(r, ts, others)).max(smallestFirst))
                .or(() -> pool.stream()
                        .filter(r -> r.getCapacity() >= activity.totalStudentCount())
                        .min(smallestFirst))
                .orElseGet(() -> pool.stream().max(smallestFirst).orElse(null));
    }

    private static boolean typeFits(ScheduledActivity a, Room r) {
        if (a.isRequiresLab()) {
            return r.getTypology() == RoomTypology.LAB;
        }
        if (a.isRequiresAmphitheater()) {
            return r.getTypology() == RoomTypology.AMPHITHEATER;
        }
        return true;
    }

    /** Free = nobody clashing in it at that hour, and not marked unavailable then. */
    private static boolean free(Room r, TimeSlot ts, List<ScheduledActivity> othersInSlot) {
        boolean taken = othersInSlot.stream()
                .anyMatch(o -> o.getRoom().getId().equals(r.getId()));
        boolean closed = r.getUnavailabilities().stream()
                .anyMatch(u -> u.overlaps(ts.getDayOfWeek(), ts.getStartTime(), ts.getEndTime()));
        return !taken && !closed;
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
        if (room != null) {
            String forbidden = forbiddenFor(activity, room, profRoomRepo.findAll());
            if (forbidden != null) {
                violations.add("Sala " + room.getName() + " e " + forbidden);
            }
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
