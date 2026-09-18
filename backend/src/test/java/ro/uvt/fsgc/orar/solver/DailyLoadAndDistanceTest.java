package ro.uvt.fsgc.orar.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.domain.WeekParity;

/**
 * Two rules about what a student's day can actually look like: at most five modules in it, and no
 * dash between P01 and the rest of the faculty in the ten minutes between two modules.
 */
class DailyLoadAndDistanceTest {

    private final ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> verifier =
            ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class,
                    ScheduledActivity.class);

    private static long ids = 1;

    private static TimeSlot slot(DayOfWeek day, int index) {
        return new TimeSlot(day.getValue() * 10L + index, day, index,
                LocalTime.of(7 + index, 0), LocalTime.of(8 + index, 30));
    }

    private static StudentGroup group(String name) {
        StudentGroup g = new StudentGroup();
        g.setId(ids++);
        g.setName(name);
        g.setSpecialization("AP");
        g.setYear(1);
        g.setStudyProgram(StudyProgram.LICENSE);
        g.setStudentCount(25);
        return g;
    }

    private static Room room(String name) {
        Room r = new Room();
        r.setId(ids++);
        r.setName(name);
        r.setCapacity(40);
        r.setTypology(RoomTypology.SEMINAR);
        return r;
    }

    private static ScheduledActivity act(TimeSlot ts, Room room, ActivityType type,
                                         WeekParity parity, StudentGroup... groups) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(ids++);
        Subject s = new Subject();
        s.setId(ids++);
        s.setCode("C" + a.getId());
        s.setName("Subject " + a.getId());
        a.setSubject(s);
        a.setActivityType(type);
        a.setTimeSlot(ts);
        a.setRoom(room);
        a.setWeekParity(parity);
        a.setStudentGroups(Set.of(groups));
        return a;
    }

    private static ScheduledActivity at(int module, Room room, StudentGroup... groups) {
        return act(slot(DayOfWeek.MONDAY, module), room, ActivityType.SEMINAR,
                WeekParity.EVERY_WEEK, groups);
    }

    // ----- at most five modules a day -----

    @Test
    void fiveModulesInADayAreFine() {
        StudentGroup g = group("G1");
        Room r = room("028");
        verifier.verifyThat(TimetableConstraintProvider::maxModulesPerDay)
                .given(g, at(1, r, g), at(2, r, g), at(3, r, g), at(4, r, g), at(5, r, g))
                .penalizesBy(0);
    }

    @Test
    void theSixthModuleIsAViolation() {
        StudentGroup g = group("G1");
        Room r = room("028");
        verifier.verifyThat(TimetableConstraintProvider::maxModulesPerDay)
                .given(g, at(1, r, g), at(2, r, g), at(3, r, g), at(4, r, g), at(5, r, g), at(6, r, g))
                .penalizesBy(1);
    }

    @Test
    void coursesAndSeminarsCountTogether() {
        StudentGroup g = group("G1");
        Room r = room("028");
        verifier.verifyThat(TimetableConstraintProvider::maxModulesPerDay)
                .given(g,
                        act(slot(DayOfWeek.MONDAY, 1), r, ActivityType.COURSE, WeekParity.EVERY_WEEK, g),
                        act(slot(DayOfWeek.MONDAY, 2), r, ActivityType.COURSE, WeekParity.EVERY_WEEK, g),
                        act(slot(DayOfWeek.MONDAY, 3), r, ActivityType.LAB, WeekParity.EVERY_WEEK, g),
                        at(4, r, g), at(5, r, g), at(6, r, g), at(7, r, g))
                .penalizesBy(2);
    }

    @Test
    void theTwoHalvesOfAnAlternatingHourAreOneModule() {
        // SI and SP sit in the same module: a student attends one of them, so it is one class.
        StudentGroup g = group("G1");
        Room r = room("028");
        TimeSlot shared = slot(DayOfWeek.MONDAY, 6);
        verifier.verifyThat(TimetableConstraintProvider::maxModulesPerDay)
                .given(g, at(1, r, g), at(2, r, g), at(3, r, g), at(4, r, g), at(5, r, g),
                        act(shared, r, ActivityType.SEMINAR, WeekParity.ODD_WEEKS, g),
                        act(shared, r, ActivityType.SEMINAR, WeekParity.EVEN_WEEKS, g))
                .penalizesBy(1);
    }

    @Test
    void daysAreCountedSeparately() {
        StudentGroup g = group("G1");
        Room r = room("028");
        verifier.verifyThat(TimetableConstraintProvider::maxModulesPerDay)
                .given(g, at(1, r, g), at(2, r, g), at(3, r, g),
                        act(slot(DayOfWeek.TUESDAY, 1), r, ActivityType.SEMINAR, WeekParity.EVERY_WEEK, g),
                        act(slot(DayOfWeek.TUESDAY, 2), r, ActivityType.SEMINAR, WeekParity.EVERY_WEEK, g),
                        act(slot(DayOfWeek.TUESDAY, 3), r, ActivityType.SEMINAR, WeekParity.EVERY_WEEK, g))
                .penalizesBy(0);
    }

    // ----- the walk to P01 -----

    @Test
    void p01ThenAnotherRoomBackToBackIsAViolation() {
        StudentGroup g = group("G1");
        verifier.verifyThat(TimetableConstraintProvider::farRoomCommute)
                .given(at(1, room("P01"), g), at(2, room("028"), g))
                .penalizesBy(1);
    }

    @Test
    void theOtherDirectionCountsToo() {
        StudentGroup g = group("G1");
        verifier.verifyThat(TimetableConstraintProvider::farRoomCommute)
                .given(at(3, room("028"), g), at(4, room("p01"), g))
                .penalizesBy(1);
    }

    @Test
    void twoHoursBothInP01AreFine() {
        StudentGroup g = group("G1");
        verifier.verifyThat(TimetableConstraintProvider::farRoomCommute)
                .given(at(1, room("P01"), g), at(2, room("P01"), g))
                .penalizesBy(0);
    }

    @Test
    void aFreeModuleInBetweenIsEnough() {
        StudentGroup g = group("G1");
        verifier.verifyThat(TimetableConstraintProvider::farRoomCommute)
                .given(at(1, room("P01"), g), at(3, room("028"), g))
                .penalizesBy(0);
    }

    @Test
    void anotherGroupsHourIsNotThisGroupsWalk() {
        verifier.verifyThat(TimetableConstraintProvider::farRoomCommute)
                .given(at(1, room("P01"), group("G1")), at(2, room("028"), group("G2")))
                .penalizesBy(0);
    }

    @Test
    void anOnlineHourIsNoWalkAtAll() {
        StudentGroup g = group("G1");
        ScheduledActivity online = at(2, null, g);
        online.setOnline(true);
        verifier.verifyThat(TimetableConstraintProvider::farRoomCommute)
                .given(at(1, room("P01"), g), online)
                .penalizesBy(0);
    }
}
