package ro.uvt.fsgc.orar.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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
 * A pinned hour is a promise: a professor asked for that time, it was placed by hand, and the
 * next generation has to build around it rather than move it.
 */
class PinnedActivityTest {

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
        g.setStudentCount(20);
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

    private static ScheduledActivity activity(StudentGroup g, Professor p) {
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
        a.setStudentGroups(Set.of(g));
        return a;
    }

    @Test
    void theSolverBuildsAroundAPinnedHour() {
        List<TimeSlot> slots = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            slots.add(slot(DayOfWeek.MONDAY, i));
        }
        Room a01 = room("A01");
        Room a02 = room("A02");
        StudentGroup g = group("G1");
        Professor prof = new Professor();
        prof.setId(ids++);
        prof.setName("Popescu");

        // the hour the professor asked for: Monday, module 4, room A02 — and pinned there
        ScheduledActivity fixed = activity(g, prof);
        fixed.setTimeSlot(slot(DayOfWeek.MONDAY, 4));
        fixed.setRoom(a02);
        fixed.setPinned(true);

        List<ScheduledActivity> activities = new ArrayList<>(List.of(fixed));
        for (int i = 0; i < 3; i++) {
            activities.add(activity(g, prof)); // free hours, same group and professor
        }

        TimetableSolution problem = new TimetableSolution();
        problem.setTimeSlots(slots);
        problem.setRooms(List.of(a01, a02));
        problem.setStudentGroups(List.of(g));
        problem.setActivities(activities);

        SolverConfig config = new SolverConfig()
                .withSolutionClass(TimetableSolution.class)
                .withEntityClasses(ScheduledActivity.class)
                .withConstraintProviderClass(TimetableConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(5L));
        TimetableSolution solved = SolverFactory.<TimetableSolution>create(config).buildSolver()
                .solve(problem);

        ScheduledActivity after = solved.getActivities().stream()
                .filter(x -> x.getId().equals(fixed.getId())).findFirst().orElseThrow();
        assertEquals(4, after.getTimeSlot().getSlotIndex(), "the pinned hour kept its module");
        assertEquals(DayOfWeek.MONDAY, after.getTimeSlot().getDayOfWeek());
        assertEquals("A02", after.getRoom().getName(), "the pinned hour kept its room");
        assertTrue(after.isPinned());

        assertEquals(0, solved.getScore().hardScore(), "the rest is built around it: "
                + solved.getScore());
        assertEquals(0, solved.getScore().mediumScore(), "everything still fits: "
                + solved.getScore());
        for (ScheduledActivity x : solved.getActivities()) {
            assertNotNull(x.getTimeSlot());
            // nothing else may share the pinned hour's module: same group, same professor
            if (!x.getId().equals(fixed.getId())) {
                assertTrue(x.getTimeSlot().getSlotIndex() != 4,
                        "another hour of the same group landed on the pinned module");
            }
        }
    }
}
