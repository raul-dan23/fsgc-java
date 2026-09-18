package ro.uvt.fsgc.orar.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.domain.WeekParity;

/**
 * What a professor's day and week should look like. The day spans the whole timetable — every
 * year and every section — because a professor teaches across all of them.
 */
class ProfessorDayTest {

    private final ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> verifier =
            ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class,
                    ScheduledActivity.class);

    private static long ids = 1;
    private static final Professor P = prof("Popescu");

    private static Professor prof(String name) {
        Professor p = new Professor();
        p.setId(ids++);
        p.setName(name);
        return p;
    }

    private static StudentGroup group(String name, String spec, int year) {
        StudentGroup g = new StudentGroup();
        g.setId(ids++);
        g.setName(name);
        g.setSpecialization(spec);
        g.setYear(year);
        g.setStudyProgram(StudyProgram.LICENSE);
        g.setStudentCount(25);
        return g;
    }

    private static Room room() {
        Room r = new Room();
        r.setId(ids++);
        r.setName("028");
        r.setCapacity(40);
        r.setTypology(RoomTypology.SEMINAR);
        return r;
    }

    private static ScheduledActivity at(DayOfWeek day, int module, Professor p, StudentGroup g) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(ids++);
        Subject s = new Subject();
        s.setId(ids++);
        s.setCode("C" + a.getId());
        s.setName("Subject " + a.getId());
        a.setSubject(s);
        a.setProfessor(p);
        a.setActivityType(ActivityType.SEMINAR);
        a.setWeekParity(WeekParity.EVERY_WEEK);
        a.setTimeSlot(new TimeSlot(day.getValue() * 10L + module, day, module,
                LocalTime.of(7 + module, 0), LocalTime.of(8 + module, 30)));
        a.setRoom(room());
        a.setStudentGroups(Set.of(g));
        return a;
    }

    private static ScheduledActivity mon(int module) {
        return at(DayOfWeek.MONDAY, module, P, group("G" + module, "AP", 1));
    }

    // ----- the day holds together -----

    @Test
    void threeModulesBackToBackAreFine() {
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(mon(1), mon(2), mon(3))
                .penalizesBy(0);
    }

    @Test
    void aGapInAThreeModuleDayIsAViolation() {
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(mon(1), mon(2), mon(4))
                .penalizesBy(1);
    }

    @Test
    void twoModulesApartAreAViolationToo() {
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(mon(1), mon(3))
                .penalizesBy(1);
    }

    @Test
    void fourModulesMayBeSplitOnce() {
        // the 2 + fereastra + 2 shape the staff asked for
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(mon(1), mon(2), mon(4), mon(5))
                .penalizesBy(0);
    }

    @Test
    void fourModulesInOneBlockAreFineAsWell() {
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(mon(1), mon(2), mon(3), mon(4))
                .penalizesBy(0);
    }

    @Test
    void theWindowMayNotBeWiderThanOneModule() {
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(mon(1), mon(2), mon(5), mon(6))
                .penalizesBy(1);
    }

    @Test
    void twoSeparateWindowsAreOneTooMany() {
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(mon(1), mon(3), mon(5), mon(7))
                .penalizesBy(2);
    }

    @Test
    void theDaySpansEveryYearAndSection() {
        // one hour with a first-year AP group, the next with a third-year RISE group: same day
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(at(DayOfWeek.MONDAY, 1, P, group("AP1", "AP", 1)),
                        at(DayOfWeek.MONDAY, 3, P, group("RISE3", "RISE", 3)))
                .penalizesBy(1);
    }

    @Test
    void anotherProfessorsHoursDoNotCount() {
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(at(DayOfWeek.MONDAY, 1, P, group("AP1", "AP", 1)),
                        at(DayOfWeek.MONDAY, 3, prof("Ionescu"), group("AP2", "AP", 2)))
                .penalizesBy(0);
    }

    @Test
    void daysAreJudgedOnTheirOwn() {
        verifier.verifyThat(TimetableConstraintProvider::professorDayGaps)
                .given(mon(1), mon(2),
                        at(DayOfWeek.TUESDAY, 5, P, group("X", "AP", 1)))
                .penalizesBy(0);
    }

    // ----- three trips a week -----

    @Test
    void threeDaysAWeekAreFine() {
        verifier.verifyThat(TimetableConstraintProvider::professorWeekDays)
                .given(at(DayOfWeek.MONDAY, 1, P, group("A", "AP", 1)),
                        at(DayOfWeek.WEDNESDAY, 1, P, group("B", "AP", 1)),
                        at(DayOfWeek.FRIDAY, 1, P, group("C", "AP", 1)))
                .penalizesBy(0);
    }

    @Test
    void oneModuleOnEachOfFiveDaysCostsTwo() {
        verifier.verifyThat(TimetableConstraintProvider::professorWeekDays)
                .given(at(DayOfWeek.MONDAY, 1, P, group("A", "AP", 1)),
                        at(DayOfWeek.TUESDAY, 1, P, group("B", "AP", 1)),
                        at(DayOfWeek.WEDNESDAY, 1, P, group("C", "AP", 1)),
                        at(DayOfWeek.THURSDAY, 1, P, group("D", "AP", 1)),
                        at(DayOfWeek.FRIDAY, 1, P, group("E", "AP", 1)))
                // two days too many, each worth EXTRA_DAY_COST before the weight
                .penalizesBy(2 * TimetableConstraintProvider.EXTRA_DAY_COST);
    }

    @Test
    void manyHoursInThreeDaysCostNothing() {
        verifier.verifyThat(TimetableConstraintProvider::professorWeekDays)
                .given(mon(1), mon(2), mon(3),
                        at(DayOfWeek.WEDNESDAY, 1, P, group("B", "AP", 1)),
                        at(DayOfWeek.WEDNESDAY, 2, P, group("C", "AP", 1)),
                        at(DayOfWeek.FRIDAY, 1, P, group("D", "AP", 1)))
                .penalizesBy(0);
    }
}
