package ro.uvt.fsgc.orar.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;
import ro.uvt.fsgc.orar.domain.BlockedDayRule;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;
import ro.uvt.fsgc.orar.domain.ProfessorUnavailability;
import ro.uvt.fsgc.orar.domain.RestrictionType;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialBlockRule;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import static ro.uvt.fsgc.orar.solver.TimetableConstraintConfiguration.*;

/**
 * All timetable constraints, each as its own named method. Weights live in
 * {@link TimetableConstraintConfiguration} (mirrored from the DB), so every constraint uses
 * {@code penalizeConfigurable()}. Hard weights are fixed at 1 hard; the solver leaves an activity
 * unassigned (1 medium) rather than break a hard rule; soft weights shape the "human" quality.
 *
 * <p>{@code forEach(ScheduledActivity.class)} only emits fully-assigned activities (both timeSlot
 * and room non-null), so room-related constraints can assume non-null planning variables — and
 * online activities, which never get a room, fall out of them by themselves. Everything that must
 * also hold for an online hour (people clashes, unavailabilities, blocked days, the soft rules
 * about a group's day) starts from {@link #placed(ConstraintFactory)} instead.
 */
public class TimetableConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory f) {
        return new Constraint[] {
                noProfessorOverlap(f),
                noRoomOverlap(f),
                noStudentGroupOverlap(f),
                roomCapacity(f),
                roomTypeMatchesActivity(f),
                labRequired(f),
                roomUnavailability(f),
                masterEveningOnly(f),
                blockedDayForTerminalYear(f),
                specialCategoryBlock(f),
                professorUnavailability(f),
                professorRoomForbidden(f),
                professorRoomOnlyThis(f),
                professorDayGaps(f),
                consecutiveSlotsSameBuilding(f),
                onlineTakesNoRoom(f),
                maxModulesPerDay(f),
                unassignedActivity(f),
                dailyLoadBalance(f),
                groupGap(f),
                lateHoursLicense(f),
                compactness(f),
                globalWeeklyBalance(f),
                professorPreference(f),
                professorWeekDays(f),
                parityPairTogether(f),
                roomOversize(f),
                farRoomCommute(f),
        };
    }

    // =========================================================== HARD constraints

    /**
     * Every activity that occupies a slot, room or not. {@code forEach} would drop the online ones
     * (their room stays null), yet they still take up the group's and the professor's hour.
     */
    private static UniConstraintStream<ScheduledActivity> placed(ConstraintFactory f) {
        return f.forEachIncludingUnassigned(ScheduledActivity.class)
                .filter(ScheduledActivity::isPlaced);
    }

    /** 1. A professor cannot teach two clashing activities in the same time slot. */
    Constraint noProfessorOverlap(ConstraintFactory f) {
        return placed(f)
                .join(placed(f),
                        Joiners.equal(ScheduledActivity::getTimeSlot),
                        Joiners.equal(ScheduledActivity::getProfessor),
                        Joiners.lessThan(ScheduledActivity::getId))
                .filter((a, b) -> a.getProfessor() != null && a.parityClashesWith(b))
                .penalizeConfigurable()
                .asConstraint(NO_PROFESSOR_OVERLAP);
    }

    /** 2. A room cannot host two clashing activities in the same time slot (parity-aware). */
    Constraint noRoomOverlap(ConstraintFactory f) {
        return f.forEachUniquePair(ScheduledActivity.class,
                        Joiners.equal(ScheduledActivity::getTimeSlot),
                        Joiners.equal(ScheduledActivity::getRoom))
                .filter((a, b) -> a.parityClashesWith(b))
                .penalizeConfigurable()
                .asConstraint(NO_ROOM_OVERLAP);
    }

    /**
     * 3. A student group cannot attend two clashing activities in the same time slot. This is the
     * rule that keeps an online hour honest: it may share the module with anything else, but never
     * with another hour of its own students.
     */
    Constraint noStudentGroupOverlap(ConstraintFactory f) {
        return placed(f)
                .join(placed(f),
                        Joiners.equal(ScheduledActivity::getTimeSlot),
                        Joiners.lessThan(ScheduledActivity::getId))
                .filter((a, b) -> a.parityClashesWith(b)
                        && !Collections.disjoint(a.getStudentGroups(), b.getStudentGroups()))
                .penalizeConfigurable()
                .asConstraint(NO_GROUP_OVERLAP);
    }

    /** 4. Room capacity must cover the (possibly combined) student count. */
    Constraint roomCapacity(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.totalStudentCount() > a.getRoom().getCapacity())
                .penalizeConfigurable(a -> a.totalStudentCount() - a.getRoom().getCapacity())
                .asConstraint(ROOM_CAPACITY);
    }

    /** 5. "Curs Amfiteatru" activities require an amphitheater room. */
    Constraint roomTypeMatchesActivity(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.isRequiresAmphitheater()
                        && a.getRoom().getTypology() != RoomTypology.AMPHITHEATER)
                .penalizeConfigurable()
                .asConstraint(AMPHITHEATER_REQUIRED);
    }

    /**
     * 5b. An activity marked "must be held in a lab" only fits a room of typology LAB. The reverse
     * is deliberately not a rule: an ordinary hour may still be taught in a lab when one is free.
     */
    Constraint labRequired(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.isRequiresLab() && a.getRoom().getTypology() != RoomTypology.LAB)
                .penalizeConfigurable()
                .asConstraint(LAB_REQUIRED);
    }

    /**
     * 6. The activity's slot must not overlap any window in which the room is marked unavailable.
     * Rooms are usable by default, so only the exceptions are stored. The constraint's public
     * name stays ROOM_AVAILABILITY so previously saved weights keep applying.
     */
    Constraint roomUnavailability(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.getRoom().getUnavailabilities().stream().anyMatch(un ->
                        un.blocks(a.getTimeSlot().getDayOfWeek(), a.getTimeSlot().getStartTime(),
                                a.getTimeSlot().getEndTime(), a.getWeekParity())))
                .penalizeConfigurable()
                .asConstraint(ROOM_AVAILABILITY);
    }

    /** 7. Master activities may only be in evening modules (6-8, 16:20-21:10). */
    Constraint masterEveningOnly(ConstraintFactory f) {
        return placed(f)
                .filter(a -> a.isMaster() && !a.getTimeSlot().isEveningModule())
                .penalizeConfigurable()
                .asConstraint(MASTER_EVENING_ONLY);
    }

    /** 8. A terminal year's blocked weekday admits no activities for that program+year. */
    Constraint blockedDayForTerminalYear(ConstraintFactory f) {
        return placed(f)
                .join(BlockedDayRule.class, Joiners.equal(
                        a -> a.getTimeSlot().getDayOfWeek(), BlockedDayRule::getDayOfWeek))
                .filter((a, rule) -> a.getStudentGroups().stream().anyMatch(g ->
                        g.getStudyProgram() == rule.getStudyProgram() && g.getYear() == rule.getYear()))
                .penalizeConfigurable()
                .asConstraint(BLOCKED_DAY);
    }

    /**
     * 9. A reserved special interval (DPPD/CCOC/DCT/...) admits nothing for that audience — the
     * only exception is an activity of the very category the interval was reserved for.
     */
    Constraint specialCategoryBlock(ConstraintFactory f) {
        return placed(f)
                .join(SpecialBlockRule.class, Joiners.equal(ScheduledActivity::getTimeSlot,
                        SpecialBlockRule::getTimeSlot))
                .filter((a, rule) -> a.getSpecialCategory() != rule.getCategory()
                        && audienceMatches(a, rule))
                .penalizeConfigurable()
                .asConstraint(SPECIAL_BLOCK);
    }

    /** 10 & 11. A professor cannot be scheduled during a day/interval they are unavailable. */
    Constraint professorUnavailability(ConstraintFactory f) {
        return placed(f)
                .filter(a -> a.getProfessor() != null)
                .join(ProfessorUnavailability.class, Joiners.equal(
                        ScheduledActivity::getProfessor, ProfessorUnavailability::getProfessor))
                .filter((a, u) -> u.blocks(a.getTimeSlot().getDayOfWeek(),
                        a.getTimeSlot().getStartTime(), a.getTimeSlot().getEndTime()))
                .penalizeConfigurable()
                .asConstraint(PROFESSOR_UNAVAILABILITY);
    }

    /** 11a. A professor may not teach in a room explicitly forbidden to them. */
    Constraint professorRoomForbidden(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.getProfessor() != null)
                .join(ProfessorRoomRestriction.class, Joiners.equal(
                        ScheduledActivity::getProfessor, ProfessorRoomRestriction::getProfessor))
                .filter((a, r) -> r.getRestrictionType() == RestrictionType.FORBIDDEN
                        && r.getRoom().equals(a.getRoom()))
                .penalizeConfigurable()
                .asConstraint(PROFESSOR_FORBIDDEN_ROOM);
    }

    /**
     * 11b. If a professor has any ONLY_THIS room rule, they may teach only in those rooms:
     * a violation is an activity whose professor has a whitelist but whose room is not on it.
     */
    Constraint professorRoomOnlyThis(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.getProfessor() != null)
                .ifExists(ProfessorRoomRestriction.class,
                        Joiners.equal(ScheduledActivity::getProfessor, ProfessorRoomRestriction::getProfessor),
                        Joiners.filtering((a, r) -> r.getRestrictionType() == RestrictionType.ONLY_THIS))
                .ifNotExists(ProfessorRoomRestriction.class,
                        Joiners.equal(ScheduledActivity::getProfessor, ProfessorRoomRestriction::getProfessor),
                        Joiners.equal(ScheduledActivity::getRoom, ProfessorRoomRestriction::getRoom),
                        Joiners.filtering((a, r) -> r.getRestrictionType() == RestrictionType.ONLY_THIS))
                .penalizeConfigurable()
                .asConstraint(PROFESSOR_ONLY_THIS_ROOM);
    }

    /**
     * 12. A teaching day must hold together. A professor with three modules or fewer teaches them
     * back to back; from four on, one single empty module is allowed — the 2 + gap + 2 shape — and
     * never a wider one. The day is the professor's whole day across every year and section, not
     * one group's timetable.
     */
    Constraint professorDayGaps(ConstraintFactory f) {
        return placed(f)
                .filter(a -> a.getProfessor() != null)
                .groupBy(ScheduledActivity::getProfessor,
                        a -> a.getTimeSlot().getDayOfWeek(),
                        ConstraintCollectors.toList(a -> a.getTimeSlot().getSlotIndex()))
                .filter((p, day, modules) -> gapCount(modules) > allowedGaps(modules))
                .penalizeConfigurable((p, day, modules) -> gapCount(modules) - allowedGaps(modules))
                .asConstraint(PROFESSOR_DAY_GAPS);
    }

    /**
     * 13. A group's consecutive (adjacent module) activities must be in the same building, or have
     * a free module between them for commuting. HARD. Unknown (null) buildings are skipped.
     */
    Constraint consecutiveSlotsSameBuilding(ConstraintFactory f) {
        return f.forEachUniquePair(ScheduledActivity.class,
                        Joiners.equal(a -> a.getTimeSlot().getDayOfWeek()))
                .filter((a, b) -> !Collections.disjoint(a.getStudentGroups(), b.getStudentGroups())
                        && Math.abs(a.getTimeSlot().getSlotIndex() - b.getTimeSlot().getSlotIndex()) == 1
                        && a.getRoom().getBuilding() != null && b.getRoom().getBuilding() != null
                        && !a.getRoom().getBuilding().equals(b.getRoom().getBuilding()))
                .penalizeConfigurable()
                .asConstraint(CONSECUTIVE_SAME_BUILDING);
    }

    /**
     * 14. A group may sit through at most five modules in one day, courses and seminars alike.
     * Counted as distinct modules, not activities: the two halves of an alternating hour fall in
     * the same module and are one class to a student, not two.
     */
    Constraint maxModulesPerDay(ConstraintFactory f) {
        return f.forEach(StudentGroup.class)
                .join(placed(f), Joiners.filtering((g, a) -> a.getStudentGroups().contains(g)))
                .groupBy((g, a) -> g, (g, a) -> a.getTimeSlot().getDayOfWeek(),
                        ConstraintCollectors.toSet((g, a) -> a.getTimeSlot().getSlotIndex()))
                .filter((g, day, modules) -> modules.size() > MAX_MODULES_A_DAY)
                .penalizeConfigurable((g, day, modules) -> modules.size() - MAX_MODULES_A_DAY)
                .asConstraint(MAX_MODULES_PER_DAY);
    }

    /**
     * 15. An online activity must not hold a room. Nothing else stops the solver from parking one
     * there, and a room it does not use would block a real class.
     */
    Constraint onlineTakesNoRoom(ConstraintFactory f) {
        return f.forEachIncludingUnassigned(ScheduledActivity.class)
                .filter(a -> a.isOnline() && a.getRoom() != null)
                .penalizeConfigurable()
                .asConstraint(ONLINE_NO_ROOM);
    }

    // =========================================================== MEDIUM constraint

    /** Maximize placement: every unassigned activity costs one medium point. */
    Constraint unassignedActivity(ConstraintFactory f) {
        return f.forEachIncludingUnassigned(ScheduledActivity.class)
                .filter(a -> !a.isPlaced())
                .penalizeConfigurable()
                .asConstraint(UNASSIGNED);
    }

    // =========================================================== SOFT constraints

    /**
     * S1. Balance each group's load across the week: penalize (busiest day - emptiest day),
     * counting empty weekdays as zero. Directly targets the real "Mon 64 / Fri 5" skew.
     */
    Constraint dailyLoadBalance(ConstraintFactory f) {
        return f.forEach(StudentGroup.class)
                .join(placed(f), Joiners.filtering((g, a) -> a.getStudentGroups().contains(g)))
                .groupBy((g, a) -> g,
                        ConstraintCollectors.toList((g, a) -> a.getTimeSlot().getDayOfWeek()))
                .penalizeConfigurable((g, days) -> busiestMinusEmptiest(days))
                .asConstraint(DAILY_LOAD_BALANCE);
    }

    /** S2. Penalize empty module gaps inside a group's day. */
    Constraint groupGap(ConstraintFactory f) {
        return f.forEach(StudentGroup.class)
                .join(placed(f), Joiners.filtering((g, a) -> a.getStudentGroups().contains(g)))
                .groupBy((g, a) -> g, (g, a) -> a.getTimeSlot().getDayOfWeek(),
                        ConstraintCollectors.toList((g, a) -> a.getTimeSlot().getSlotIndex()))
                .penalizeConfigurable((g, day, slots) -> gapCount(slots))
                .asConstraint(GROUP_GAP);
    }

    /** S3. Penalize late modules (7-8) for license groups, more for module 8 than 7. */
    Constraint lateHoursLicense(ConstraintFactory f) {
        return placed(f)
                .filter(a -> !a.isMaster() && a.getTimeSlot().getSlotIndex() >= 7)
                .penalizeConfigurable(a -> a.getTimeSlot().getSlotIndex() - 6)
                .asConstraint(LATE_HOURS_LICENSE);
    }

    /** S4. Prefer a group's day to start in the morning (small compactness nudge). */
    Constraint compactness(ConstraintFactory f) {
        return f.forEach(StudentGroup.class)
                .join(placed(f), Joiners.filtering((g, a) -> a.getStudentGroups().contains(g)))
                .groupBy((g, a) -> g, (g, a) -> a.getTimeSlot().getDayOfWeek(),
                        ConstraintCollectors.min((StudentGroup g, ScheduledActivity a) ->
                                a.getTimeSlot().getSlotIndex()))
                .penalizeConfigurable((g, day, firstSlot) -> firstSlot - 1)
                .asConstraint(COMPACTNESS);
    }

    /**
     * S5. Faculty-wide weekly balance: discourage piling the whole faculty onto a few days.
     *
     * <p>Counts how far a day is from an even share, not the square of its load. The square was
     * the bug behind a timetable nobody liked: 238 hours over five days scored about 11,000 units
     * before any weight, so at weight 100 this one constraint was 79% of the whole soft score and
     * every other preference — gaps in a student's day above all — was noise beside it. Worse, the
     * number barely moves between a good timetable and a bad one, so most of that mass was a
     * constant the solver could not act on, while its gradient pushed hours apart across the week,
     * which is exactly what opens gaps.
     */
    Constraint globalWeeklyBalance(ConstraintFactory f) {
        return placed(f)
                .groupBy(a -> a.getTimeSlot().getDayOfWeek(), ConstraintCollectors.count())
                .join(placed(f).groupBy(ConstraintCollectors.count()),
                        Joiners.filtering((day, count, total) -> true))
                .penalizeConfigurable((day, count, total) ->
                        Math.abs(count - Math.round((float) total / WEEKDAYS)))
                .asConstraint(GLOBAL_WEEKLY_BALANCE);
    }

    /**
     * S6. Professor time preferences (optional, entity not modeled yet). Declared so its weight is
     * consumed; matches nothing today and therefore has no effect until preferences are added.
     */
    Constraint professorPreference(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> false)
                .penalizeConfigurable()
                .asConstraint(PROFESSOR_PREFERENCE);
    }

    /**
     * S6b. Three trips to the faculty a week, not five. One module on each of five days is a bad
     * week for a professor even when every day is tidy, so every day beyond the third costs.
     * Soft: a professor with many hours may genuinely need a fourth day.
     */
    Constraint professorWeekDays(ConstraintFactory f) {
        return placed(f)
                .filter(a -> a.getProfessor() != null)
                .groupBy(ScheduledActivity::getProfessor,
                        ConstraintCollectors.toSet(a -> a.getTimeSlot().getDayOfWeek()))
                .filter((p, days) -> days.size() > MAX_PROFESSOR_DAYS)
                .penalizeConfigurable((p, days) ->
                        (days.size() - MAX_PROFESSOR_DAYS) * EXTRA_DAY_COST)
                .asConstraint(PROFESSOR_WEEK_DAYS);
    }

    /**
     * S7. The two halves of one alternating hour (same {@code parityPairKey}, different parity)
     * belong in the same slot and the same room: in the real timetable they are a single cell
     * read "SI / SP", not two hours on different days. Soft, so the solver may still break the
     * pair apart when nothing else fits. Penalty grows with how far apart they landed, which
     * gives local search a gradient to follow: 2 for a different slot, 1 for a different room.
     */
    Constraint parityPairTogether(ConstraintFactory f) {
        // Both sides are filtered to keyed activities before joining: a null key must not act as
        // a join value, or every unpaired activity would match every other one.
        return placed(f)
                .filter(a -> a.getParityPairKey() != null)
                .join(placed(f).filter(b -> b.getParityPairKey() != null),
                        Joiners.equal(ScheduledActivity::getParityPairKey),
                        Joiners.lessThan(ScheduledActivity::getId))
                .filter((a, b) -> a.getWeekParity() != b.getWeekParity())
                .penalizeConfigurable(TimetableConstraintProvider::pairSeparation)
                .asConstraint(PARITY_PAIR_TOGETHER);
    }

    /**
     * S8. Prefer the smallest room that fits: penalize every empty seat. A 27-student seminar in
     * a 150-seat amphitheatre wastes 123 seats and costs far more than the same seminar in a
     * 40-seat room (13), so the solver drifts towards right-sized rooms and leaves the two
     * amphitheatres for the trunchi-comun courses that genuinely need them.
     *
     * <p>Deliberately soft, not hard: some seminars really are attended by 49-63 students and no
     * seminar room holds more than 40, so a hard rule would leave them unplaceable. Activities
     * that require an amphitheatre are skipped — their waste is unavoidable, and scoring it would
     * only add a constant the solver cannot act on.
     */
    Constraint roomOversize(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> !a.isRequiresAmphitheater())
                .penalizeConfigurable(TimetableConstraintProvider::wastedSeats)
                .asConstraint(ROOM_OVERSIZE);
    }

    /**
     * S9. P01 is a 20-minute walk from the rest of the faculty, while the break between two
     * modules is 10 minutes: a group sent from P01 straight into another room (or the other way
     * round) cannot physically get there. Soft on purpose — P01 is the only room out there, and a
     * hard rule could leave hours unplaced rather than merely awkward — but weighted high enough
     * that the solver only does it when nothing else fits.
     *
     * <p>Online hours have no room and are not a walk, so they never trigger this.
     */
    Constraint farRoomCommute(ConstraintFactory f) {
        return f.forEachUniquePair(ScheduledActivity.class,
                        Joiners.equal(a -> a.getTimeSlot().getDayOfWeek()))
                .filter((a, b) -> Math.abs(a.getTimeSlot().getSlotIndex()
                        - b.getTimeSlot().getSlotIndex()) == 1
                        && !Collections.disjoint(a.getStudentGroups(), b.getStudentGroups())
                        && tooFarApart(a.getRoom(), b.getRoom()))
                .penalizeConfigurable()
                .asConstraint(FAR_ROOM_COMMUTE);
    }

    // =========================================================== helpers

    /** At most five modules in a day for one group, courses and seminars together. */
    public static final int MAX_MODULES_A_DAY = 5;

    /** Monday to Friday: the timetable has no weekend. */
    static final int WEEKDAYS = 5;

    /** Days a week a professor should have to come in; beyond this it only costs soft points. */
    public static final int MAX_PROFESSOR_DAYS = 3;

    /**
     * What one day too many is worth before the weight is applied. Soft constraints do not count
     * in the same units — room oversize adds up one point per empty seat and reaches six figures,
     * while this one counts whole days and would reach twenty. Without this factor a weight of 100
     * here loses to a weight of 5 there, and the slider looks broken; measured on the real data, a
     * professor's week only compacts once an extra day is worth about a thousand points.
     */
    static final int EXTRA_DAY_COST = 20;

    /** A short day is taught in one block; four modules or more may be split once, by one module. */
    static int allowedGaps(List<Integer> modules) {
        return modules.stream().distinct().count() >= 4 ? 1 : 0;
    }

    /**
     * Rooms too far from the rest of the faculty to reach in the ten minutes between two modules.
     * Kept here rather than on the Room entity because there is exactly one of them and a re-import
     * would wipe a field on the room; revisit if a second distant room ever appears.
     */
    static final java.util.Set<String> FAR_ROOMS = java.util.Set.of("P01");

    /** True when exactly one of the two rooms is out at the far end: that is the walk nobody makes. */
    static boolean tooFarApart(Room a, Room b) {
        if (a == null || b == null) {
            return false; // an online hour is attended from wherever the student already is
        }
        return isFar(a) != isFar(b);
    }

    private static boolean isFar(Room r) {
        return r.getName() != null && FAR_ROOMS.contains(r.getName().trim().toUpperCase());
    }

    /** Empty seats left by an activity, never negative (capacity shortfall is a hard constraint). */
    static int wastedSeats(ScheduledActivity a) {
        return Math.max(0, a.getRoom().getCapacity() - a.totalStudentCount());
    }


    /** 0 when both halves share slot and room, up to 3 when they share neither. */
    static int pairSeparation(ScheduledActivity a, ScheduledActivity b) {
        int penalty = 0;
        if (!a.getTimeSlot().getId().equals(b.getTimeSlot().getId())) {
            penalty += 2;
        }
        // two online halves share the same "nowhere" and are not apart
        if (!sameRoom(a, b)) {
            penalty += 1;
        }
        return penalty;
    }

    private static boolean sameRoom(ScheduledActivity a, ScheduledActivity b) {
        if (a.getRoom() == null || b.getRoom() == null) {
            return a.getRoom() == b.getRoom();
        }
        return a.getRoom().getId().equals(b.getRoom().getId());
    }


    private static boolean audienceMatches(ScheduledActivity a, SpecialBlockRule rule) {
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

    /** Busiest minus emptiest day count for a group, counting all 5 weekdays (empty = 0). */
    static int busiestMinusEmptiest(List<DayOfWeek> days) {
        int[] counts = new int[5]; // Mon..Fri
        for (DayOfWeek d : days) {
            int idx = d.getValue() - 1; // MONDAY=1
            if (idx >= 0 && idx < 5) {
                counts[idx]++;
            }
        }
        int max = 0;
        int min = Integer.MAX_VALUE;
        for (int c : counts) {
            max = Math.max(max, c);
            min = Math.min(min, c);
        }
        return max - min;
    }

    /** Number of empty modules between the first and last occupied module on a day. */
    static int gapCount(List<Integer> slotIndices) {
        List<Integer> sorted = slotIndices.stream().distinct().sorted().toList();
        int gaps = 0;
        for (int i = 1; i < sorted.size(); i++) {
            gaps += sorted.get(i) - sorted.get(i - 1) - 1;
        }
        return gaps;
    }
}
