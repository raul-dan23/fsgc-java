package ro.uvt.fsgc.orar.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;
import ro.uvt.fsgc.orar.domain.BlockedDayRule;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;
import ro.uvt.fsgc.orar.domain.ProfessorUnavailability;
import ro.uvt.fsgc.orar.domain.RestrictionType;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialBlockRule;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import static ro.uvt.fsgc.orar.solver.TimetableConstraintConfiguration.*;

/**
 * All timetable constraints, each as its own named method. Weights live in
 * {@link TimetableConstraintConfiguration} (mirrored from the DB), so every constraint uses
 * {@code penalizeConfigurable()}. Hard weights are fixed at 1 hard; the solver leaves an activity
 * unassigned (1 medium) rather than break a hard rule; soft weights shape the "human" quality.
 *
 * <p>{@code forEach(ScheduledActivity.class)} only emits fully-assigned activities (both timeSlot
 * and room non-null), so the constraints below can assume non-null planning variables.
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
                roomUnavailability(f),
                masterEveningOnly(f),
                blockedDayForTerminalYear(f),
                specialCategoryBlock(f),
                professorUnavailability(f),
                professorRoomForbidden(f),
                professorRoomOnlyThis(f),
                consecutiveSlotsSameBuilding(f),
                unassignedActivity(f),
                dailyLoadBalance(f),
                groupGap(f),
                lateHoursLicense(f),
                compactness(f),
                globalWeeklyBalance(f),
                professorPreference(f),
                parityPairTogether(f),
        };
    }

    // =========================================================== HARD constraints

    /** 1. A professor cannot teach two clashing activities in the same time slot. */
    Constraint noProfessorOverlap(ConstraintFactory f) {
        return f.forEachUniquePair(ScheduledActivity.class,
                        Joiners.equal(ScheduledActivity::getTimeSlot),
                        Joiners.equal(ScheduledActivity::getProfessor))
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

    /** 3. A student group cannot attend two clashing activities in the same time slot. */
    Constraint noStudentGroupOverlap(ConstraintFactory f) {
        return f.forEachUniquePair(ScheduledActivity.class,
                        Joiners.equal(ScheduledActivity::getTimeSlot))
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
     * 6. The activity's slot must not overlap any window in which the room is marked unavailable.
     * Rooms are usable by default, so only the exceptions are stored. The constraint's public
     * name stays ROOM_AVAILABILITY so previously saved weights keep applying.
     */
    Constraint roomUnavailability(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.getRoom().getUnavailabilities().stream().anyMatch(un ->
                        un.overlaps(a.getTimeSlot().getDayOfWeek(),
                                a.getTimeSlot().getStartTime(), a.getTimeSlot().getEndTime())))
                .penalizeConfigurable()
                .asConstraint(ROOM_AVAILABILITY);
    }

    /** 7. Master activities may only be in evening modules (6-8, 16:20-21:10). */
    Constraint masterEveningOnly(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.isMaster() && !a.getTimeSlot().isEveningModule())
                .penalizeConfigurable()
                .asConstraint(MASTER_EVENING_ONLY);
    }

    /** 8. A terminal year's blocked weekday admits no activities for that program+year. */
    Constraint blockedDayForTerminalYear(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .join(BlockedDayRule.class, Joiners.equal(
                        a -> a.getTimeSlot().getDayOfWeek(), BlockedDayRule::getDayOfWeek))
                .filter((a, rule) -> a.getStudentGroups().stream().anyMatch(g ->
                        g.getStudyProgram() == rule.getStudyProgram() && g.getYear() == rule.getYear()))
                .penalizeConfigurable()
                .asConstraint(BLOCKED_DAY);
    }

    /** 9. A reserved special interval (DPPD/CCOC/DCT/...) admits no normal activity for that audience. */
    Constraint specialCategoryBlock(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.getSpecialCategory() == SpecialCategory.NORMAL)
                .join(SpecialBlockRule.class, Joiners.equal(ScheduledActivity::getTimeSlot,
                        SpecialBlockRule::getTimeSlot))
                .filter((a, rule) -> audienceMatches(a, rule))
                .penalizeConfigurable()
                .asConstraint(SPECIAL_BLOCK);
    }

    /** 10 & 11. A professor cannot be scheduled during a day/interval they are unavailable. */
    Constraint professorUnavailability(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.getProfessor() != null)
                .join(ProfessorUnavailability.class, Joiners.equal(
                        ScheduledActivity::getProfessor, ProfessorUnavailability::getProfessor))
                .filter((a, u) -> u.blocks(a.getTimeSlot().getDayOfWeek(),
                        a.getTimeSlot().getStartTime(), a.getTimeSlot().getEndTime()))
                .penalizeConfigurable()
                .asConstraint(PROFESSOR_UNAVAILABILITY);
    }

    /** 12a. A professor may not teach in a room explicitly forbidden to them. */
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
     * 12b. If a professor has any ONLY_THIS room rule, they may teach only in those rooms:
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

    // =========================================================== MEDIUM constraint

    /** Maximize placement: every unassigned activity costs one medium point. */
    Constraint unassignedActivity(ConstraintFactory f) {
        return f.forEachIncludingUnassigned(ScheduledActivity.class)
                .filter(a -> a.getTimeSlot() == null || a.getRoom() == null)
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
                .join(ScheduledActivity.class,
                        Joiners.filtering((g, a) -> a.getStudentGroups().contains(g)))
                .groupBy((g, a) -> g,
                        ConstraintCollectors.toList((g, a) -> a.getTimeSlot().getDayOfWeek()))
                .penalizeConfigurable((g, days) -> busiestMinusEmptiest(days))
                .asConstraint(DAILY_LOAD_BALANCE);
    }

    /** S2. Penalize empty module gaps inside a group's day. */
    Constraint groupGap(ConstraintFactory f) {
        return f.forEach(StudentGroup.class)
                .join(ScheduledActivity.class,
                        Joiners.filtering((g, a) -> a.getStudentGroups().contains(g)))
                .groupBy((g, a) -> g, (g, a) -> a.getTimeSlot().getDayOfWeek(),
                        ConstraintCollectors.toList((g, a) -> a.getTimeSlot().getSlotIndex()))
                .penalizeConfigurable((g, day, slots) -> gapCount(slots))
                .asConstraint(GROUP_GAP);
    }

    /** S3. Penalize late modules (7-8) for license groups, more for module 8 than 7. */
    Constraint lateHoursLicense(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .filter(a -> !a.isMaster() && a.getTimeSlot().getSlotIndex() >= 7)
                .penalizeConfigurable(a -> a.getTimeSlot().getSlotIndex() - 6)
                .asConstraint(LATE_HOURS_LICENSE);
    }

    /** S4. Prefer a group's day to start in the morning (small compactness nudge). */
    Constraint compactness(ConstraintFactory f) {
        return f.forEach(StudentGroup.class)
                .join(ScheduledActivity.class,
                        Joiners.filtering((g, a) -> a.getStudentGroups().contains(g)))
                .groupBy((g, a) -> g, (g, a) -> a.getTimeSlot().getDayOfWeek(),
                        ConstraintCollectors.min((StudentGroup g, ScheduledActivity a) ->
                                a.getTimeSlot().getSlotIndex()))
                .penalizeConfigurable((g, day, firstSlot) -> firstSlot - 1)
                .asConstraint(COMPACTNESS);
    }

    /** S5. Faculty-wide weekly balance: discourage piling activities on a few days (sum of squares). */
    Constraint globalWeeklyBalance(ConstraintFactory f) {
        return f.forEach(ScheduledActivity.class)
                .groupBy(a -> a.getTimeSlot().getDayOfWeek(), ConstraintCollectors.count())
                .penalizeConfigurable((day, count) -> count * count)
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
     * S7. The two halves of one alternating hour (same {@code parityPairKey}, different parity)
     * belong in the same slot and the same room: in the real timetable they are a single cell
     * read "SI / SP", not two hours on different days. Soft, so the solver may still break the
     * pair apart when nothing else fits. Penalty grows with how far apart they landed, which
     * gives local search a gradient to follow: 2 for a different slot, 1 for a different room.
     */
    Constraint parityPairTogether(ConstraintFactory f) {
        // Both sides are filtered to keyed activities before joining: a null key must not act as
        // a join value, or every unpaired activity would match every other one.
        return f.forEach(ScheduledActivity.class)
                .filter(a -> a.getParityPairKey() != null)
                .join(f.forEach(ScheduledActivity.class)
                                .filter(b -> b.getParityPairKey() != null),
                        Joiners.equal(ScheduledActivity::getParityPairKey),
                        Joiners.lessThan(ScheduledActivity::getId))
                .filter((a, b) -> a.getWeekParity() != b.getWeekParity())
                .penalizeConfigurable(TimetableConstraintProvider::pairSeparation)
                .asConstraint(PARITY_PAIR_TOGETHER);
    }

    // =========================================================== helpers

    /** 0 when both halves share slot and room, up to 3 when they share neither. */
    static int pairSeparation(ScheduledActivity a, ScheduledActivity b) {
        int penalty = 0;
        if (!a.getTimeSlot().getId().equals(b.getTimeSlot().getId())) {
            penalty += 2;
        }
        if (!a.getRoom().getId().equals(b.getRoom().getId())) {
            penalty += 1;
        }
        return penalty;
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
