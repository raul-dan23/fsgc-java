package ro.uvt.fsgc.orar.solver;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintConfiguration;
import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

/**
 * Weights for EVERY constraint. Timefold requires that, when a constraint configuration is used,
 * every constraint in the {@link TimetableConstraintProvider} has a matching {@code @ConstraintWeight}.
 * Hard constraints are fixed at 1 hard (non-negotiable), the unassigned penalty at 1 medium, and the
 * eight quality constraints carry tunable soft weights mirrored from the {@code constraint_weights} DB row.
 */
@ConstraintConfiguration(constraintPackage = "ro.uvt.fsgc.orar.solver")
public class TimetableConstraintConfiguration {

    // ---- hard constraint names ----
    public static final String NO_PROFESSOR_OVERLAP = "No professor overlap";
    public static final String NO_ROOM_OVERLAP = "No room overlap";
    public static final String NO_GROUP_OVERLAP = "No student group overlap";
    public static final String ROOM_CAPACITY = "Room capacity sufficient";
    public static final String AMPHITHEATER_REQUIRED = "Amphitheater required";
    public static final String LAB_REQUIRED = "Lab required";
    public static final String ROOM_AVAILABILITY = "Room availability";
    public static final String MASTER_EVENING_ONLY = "Master evening only";
    public static final String BLOCKED_DAY = "Blocked day for terminal year";
    public static final String SPECIAL_BLOCK = "Special category block";
    public static final String PROFESSOR_UNAVAILABILITY = "Professor unavailability";
    public static final String PROFESSOR_FORBIDDEN_ROOM = "Professor forbidden room";
    public static final String PROFESSOR_ONLY_THIS_ROOM = "Professor only-this room";
    public static final String PROFESSOR_DAY_GAPS = "Professor day holds together";
    public static final String CONSECUTIVE_SAME_BUILDING = "Consecutive slots same building";
    public static final String ONLINE_NO_ROOM = "Online activity takes no room";
    public static final String MAX_MODULES_PER_DAY = "At most five modules a day per group";
    // ---- medium ----
    public static final String UNASSIGNED = "Unassigned activity";
    // ---- soft (tunable) ----
    public static final String DAILY_LOAD_BALANCE = "Daily load balance per group";
    public static final String GROUP_GAP = "Avoid gaps in a group's day";
    public static final String LATE_HOURS_LICENSE = "Avoid late hours for license groups";
    public static final String COMPACTNESS = "Compact a group's day";
    public static final String GLOBAL_WEEKLY_BALANCE = "Faculty-wide weekly balance";
    public static final String PROFESSOR_PREFERENCE = "Honor professor time preferences";
    public static final String PROFESSOR_WEEK_DAYS = "At most three teaching days a week";
    public static final String PARITY_PAIR_TOGETHER = "Alternating halves share slot and room";
    public static final String ROOM_OVERSIZE = "Prefer the smallest adequate room";
    public static final String FAR_ROOM_COMMUTE = "Time to walk to a far room";

    // ---- hard weights (fixed) ----
    @ConstraintWeight(NO_PROFESSOR_OVERLAP)
    private HardMediumSoftScore noProfessorOverlap = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(NO_ROOM_OVERLAP)
    private HardMediumSoftScore noRoomOverlap = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(NO_GROUP_OVERLAP)
    private HardMediumSoftScore noGroupOverlap = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(ROOM_CAPACITY)
    private HardMediumSoftScore roomCapacity = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(AMPHITHEATER_REQUIRED)
    private HardMediumSoftScore amphitheaterRequired = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(LAB_REQUIRED)
    private HardMediumSoftScore labRequired = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(ROOM_AVAILABILITY)
    private HardMediumSoftScore roomAvailability = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(MASTER_EVENING_ONLY)
    private HardMediumSoftScore masterEveningOnly = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(BLOCKED_DAY)
    private HardMediumSoftScore blockedDay = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(SPECIAL_BLOCK)
    private HardMediumSoftScore specialBlock = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(PROFESSOR_UNAVAILABILITY)
    private HardMediumSoftScore professorUnavailability = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(PROFESSOR_FORBIDDEN_ROOM)
    private HardMediumSoftScore professorForbiddenRoom = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(PROFESSOR_ONLY_THIS_ROOM)
    private HardMediumSoftScore professorOnlyThisRoom = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(PROFESSOR_DAY_GAPS)
    private HardMediumSoftScore professorDayGaps = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(CONSECUTIVE_SAME_BUILDING)
    private HardMediumSoftScore consecutiveSameBuilding = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(ONLINE_NO_ROOM)
    private HardMediumSoftScore onlineNoRoom = HardMediumSoftScore.ofHard(1);
    @ConstraintWeight(MAX_MODULES_PER_DAY)
    private HardMediumSoftScore maxModulesPerDay = HardMediumSoftScore.ofHard(1);

    // ---- medium weight ----
    @ConstraintWeight(UNASSIGNED)
    private HardMediumSoftScore unassigned = HardMediumSoftScore.ofMedium(1);

    // ---- soft weights (tunable) ----
    @ConstraintWeight(DAILY_LOAD_BALANCE)
    private HardMediumSoftScore dailyLoadBalance = HardMediumSoftScore.ofSoft(40);
    @ConstraintWeight(GROUP_GAP)
    private HardMediumSoftScore groupGap = HardMediumSoftScore.ofSoft(30);
    @ConstraintWeight(LATE_HOURS_LICENSE)
    private HardMediumSoftScore lateHoursLicense = HardMediumSoftScore.ofSoft(15);
    @ConstraintWeight(COMPACTNESS)
    private HardMediumSoftScore compactness = HardMediumSoftScore.ofSoft(8);
    @ConstraintWeight(GLOBAL_WEEKLY_BALANCE)
    private HardMediumSoftScore globalWeeklyBalance = HardMediumSoftScore.ofSoft(5);
    @ConstraintWeight(PROFESSOR_PREFERENCE)
    private HardMediumSoftScore professorPreference = HardMediumSoftScore.ofSoft(5);
    @ConstraintWeight(PROFESSOR_WEEK_DAYS)
    private HardMediumSoftScore professorWeekDays = HardMediumSoftScore.ofSoft(60);
    @ConstraintWeight(PARITY_PAIR_TOGETHER)
    private HardMediumSoftScore parityPairTogether = HardMediumSoftScore.ofSoft(50);
    @ConstraintWeight(ROOM_OVERSIZE)
    private HardMediumSoftScore roomOversize = HardMediumSoftScore.ofSoft(2);
    @ConstraintWeight(FAR_ROOM_COMMUTE)
    private HardMediumSoftScore farRoomCommute = HardMediumSoftScore.ofSoft(80);

    // Hard/medium getters are required by Timefold to read the weights.
    public HardMediumSoftScore getNoProfessorOverlap() { return noProfessorOverlap; }
    public HardMediumSoftScore getNoRoomOverlap() { return noRoomOverlap; }
    public HardMediumSoftScore getNoGroupOverlap() { return noGroupOverlap; }
    public HardMediumSoftScore getRoomCapacity() { return roomCapacity; }
    public HardMediumSoftScore getAmphitheaterRequired() { return amphitheaterRequired; }
    public HardMediumSoftScore getLabRequired() { return labRequired; }
    public HardMediumSoftScore getRoomAvailability() { return roomAvailability; }
    public HardMediumSoftScore getMasterEveningOnly() { return masterEveningOnly; }
    public HardMediumSoftScore getBlockedDay() { return blockedDay; }
    public HardMediumSoftScore getSpecialBlock() { return specialBlock; }
    public HardMediumSoftScore getProfessorUnavailability() { return professorUnavailability; }
    public HardMediumSoftScore getProfessorForbiddenRoom() { return professorForbiddenRoom; }
    public HardMediumSoftScore getProfessorOnlyThisRoom() { return professorOnlyThisRoom; }
    public HardMediumSoftScore getProfessorDayGaps() { return professorDayGaps; }
    public HardMediumSoftScore getConsecutiveSameBuilding() { return consecutiveSameBuilding; }
    public HardMediumSoftScore getOnlineNoRoom() { return onlineNoRoom; }
    public HardMediumSoftScore getMaxModulesPerDay() { return maxModulesPerDay; }
    public HardMediumSoftScore getUnassigned() { return unassigned; }

    public HardMediumSoftScore getDailyLoadBalance() { return dailyLoadBalance; }
    public void setDailyLoadBalance(HardMediumSoftScore v) { this.dailyLoadBalance = v; }
    public HardMediumSoftScore getGroupGap() { return groupGap; }
    public void setGroupGap(HardMediumSoftScore v) { this.groupGap = v; }
    public HardMediumSoftScore getLateHoursLicense() { return lateHoursLicense; }
    public void setLateHoursLicense(HardMediumSoftScore v) { this.lateHoursLicense = v; }
    public HardMediumSoftScore getCompactness() { return compactness; }
    public void setCompactness(HardMediumSoftScore v) { this.compactness = v; }
    public HardMediumSoftScore getGlobalWeeklyBalance() { return globalWeeklyBalance; }
    public void setGlobalWeeklyBalance(HardMediumSoftScore v) { this.globalWeeklyBalance = v; }
    public HardMediumSoftScore getProfessorPreference() { return professorPreference; }
    public void setProfessorPreference(HardMediumSoftScore v) { this.professorPreference = v; }
    public HardMediumSoftScore getProfessorWeekDays() { return professorWeekDays; }
    public void setProfessorWeekDays(HardMediumSoftScore v) { this.professorWeekDays = v; }
    public HardMediumSoftScore getParityPairTogether() { return parityPairTogether; }
    public void setParityPairTogether(HardMediumSoftScore v) { this.parityPairTogether = v; }
    public HardMediumSoftScore getRoomOversize() { return roomOversize; }
    public void setRoomOversize(HardMediumSoftScore v) { this.roomOversize = v; }
    public HardMediumSoftScore getFarRoomCommute() { return farRoomCommute; }
    public void setFarRoomCommute(HardMediumSoftScore v) { this.farRoomCommute = v; }
}
