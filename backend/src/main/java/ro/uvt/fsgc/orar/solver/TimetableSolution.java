package ro.uvt.fsgc.orar.solver;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintConfigurationProvider;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import java.util.ArrayList;
import java.util.List;
import ro.uvt.fsgc.orar.domain.BlockedDayRule;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;
import ro.uvt.fsgc.orar.domain.ProfessorUnavailability;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialBlockRule;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.TimeSlot;

/**
 * The full timetable problem + solution. Activities are the planning entities; time slots and
 * rooms are the value ranges the solver assigns. Rules are problem facts the constraints join on.
 */
@PlanningSolution
public class TimetableSolution {

    @PlanningEntityCollectionProperty
    private List<ScheduledActivity> activities = new ArrayList<>();

    @ValueRangeProvider(id = "timeSlotRange")
    @ProblemFactCollectionProperty
    private List<TimeSlot> timeSlots = new ArrayList<>();

    @ValueRangeProvider(id = "roomRange")
    @ProblemFactCollectionProperty
    private List<Room> rooms = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<StudentGroup> studentGroups = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<BlockedDayRule> blockedDayRules = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<SpecialBlockRule> specialBlockRules = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<ProfessorUnavailability> professorUnavailabilities = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<ProfessorRoomRestriction> professorRoomRestrictions = new ArrayList<>();

    @ConstraintConfigurationProvider
    private TimetableConstraintConfiguration constraintConfiguration = new TimetableConstraintConfiguration();

    @PlanningScore
    private HardMediumSoftScore score;

    public TimetableSolution() {
    }

    public List<ScheduledActivity> getActivities() {
        return activities;
    }

    public void setActivities(List<ScheduledActivity> activities) {
        this.activities = activities;
    }

    public List<TimeSlot> getTimeSlots() {
        return timeSlots;
    }

    public void setTimeSlots(List<TimeSlot> timeSlots) {
        this.timeSlots = timeSlots;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }

    public List<StudentGroup> getStudentGroups() {
        return studentGroups;
    }

    public void setStudentGroups(List<StudentGroup> studentGroups) {
        this.studentGroups = studentGroups;
    }

    public List<BlockedDayRule> getBlockedDayRules() {
        return blockedDayRules;
    }

    public void setBlockedDayRules(List<BlockedDayRule> blockedDayRules) {
        this.blockedDayRules = blockedDayRules;
    }

    public List<SpecialBlockRule> getSpecialBlockRules() {
        return specialBlockRules;
    }

    public void setSpecialBlockRules(List<SpecialBlockRule> specialBlockRules) {
        this.specialBlockRules = specialBlockRules;
    }

    public List<ProfessorUnavailability> getProfessorUnavailabilities() {
        return professorUnavailabilities;
    }

    public void setProfessorUnavailabilities(List<ProfessorUnavailability> professorUnavailabilities) {
        this.professorUnavailabilities = professorUnavailabilities;
    }

    public List<ProfessorRoomRestriction> getProfessorRoomRestrictions() {
        return professorRoomRestrictions;
    }

    public void setProfessorRoomRestrictions(List<ProfessorRoomRestriction> restrictions) {
        this.professorRoomRestrictions = restrictions;
    }

    public TimetableConstraintConfiguration getConstraintConfiguration() {
        return constraintConfiguration;
    }

    public void setConstraintConfiguration(TimetableConstraintConfiguration constraintConfiguration) {
        this.constraintConfiguration = constraintConfiguration;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}
