package ro.uvt.fsgc.orar.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.ProfessorUnavailability;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.domain.WeekParity;

/**
 * An online activity takes an hour but no room. These tests pin both halves of that: it is exempt
 * from everything about rooms, and still bound by everything about people.
 */
class OnlineActivityTest {

    private final ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> verifier =
            ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class,
                    ScheduledActivity.class);

    private static long ids = 1;

    // ----- builders -----

    private static TimeSlot slot(DayOfWeek day, int index) {
        return new TimeSlot(day.getValue() * 10L + index, day, index,
                LocalTime.of(7 + index, 0), LocalTime.of(8 + index, 30));
    }

    private static StudentGroup group(String name, int size) {
        StudentGroup g = new StudentGroup();
        g.setId(ids++);
        g.setName(name);
        g.setSpecialization("AP");
        g.setYear(1);
        g.setStudyProgram(StudyProgram.LICENSE);
        g.setStudentCount(size);
        return g;
    }

    private static Room room(String name, int capacity) {
        Room r = new Room();
        r.setId(ids++);
        r.setName(name);
        r.setCapacity(capacity);
        r.setTypology(RoomTypology.SEMINAR);
        return r;
    }

    private static Professor prof(String name) {
        Professor p = new Professor();
        p.setId(ids++);
        p.setName(name);
        return p;
    }

    private static ScheduledActivity activity(Professor p, TimeSlot ts, Room room, boolean online,
                                              StudentGroup... groups) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(ids++);
        Subject s = new Subject();
        s.setId(ids++);
        s.setCode("C" + a.getId());
        s.setName("Subject " + a.getId());
        a.setSubject(s);
        a.setProfessor(p);
        a.setActivityType(ActivityType.SEMINAR);
        a.setTimeSlot(ts);
        a.setRoom(room);
        a.setOnline(online);
        a.setWeekParity(WeekParity.EVERY_WEEK);
        a.setStudentGroups(Set.of(groups));
        return a;
    }

    // ----- what an online hour is exempt from -----

    @Test
    void onlineActivity_doesNotOccupyARoom() {
        TimeSlot ts = slot(DayOfWeek.MONDAY, 2);
        Room r = room("A01", 40);
        verifier.verifyThat(TimetableConstraintProvider::noRoomOverlap)
                .given(activity(prof("X"), ts, r, false, group("G1", 20)),
                        activity(prof("Y"), ts, null, true, group("G2", 20)))
                .penalizes(0);
    }

    @Test
    void twoOnlineActivities_doNotClashWithEachOther() {
        TimeSlot ts = slot(DayOfWeek.MONDAY, 2);
        verifier.verifyThat(TimetableConstraintProvider::noRoomOverlap)
                .given(activity(prof("X"), ts, null, true, group("G1", 20)),
                        activity(prof("Y"), ts, null, true, group("G2", 20)))
                .penalizes(0);
    }

    @Test
    void onlineActivity_needsNoRoomToCountAsPlaced() {
        verifier.verifyThat(TimetableConstraintProvider::unassignedActivity)
                .given(activity(prof("X"), slot(DayOfWeek.MONDAY, 2), null, true, group("G1", 20)))
                .penalizes(0);
    }

    @Test
    void ordinaryActivityWithoutARoom_isStillUnplaced() {
        verifier.verifyThat(TimetableConstraintProvider::unassignedActivity)
                .given(activity(prof("X"), slot(DayOfWeek.MONDAY, 2), null, false, group("G1", 20)))
                .penalizes(1);
    }

    @Test
    void onlineActivity_mayNotHoldARoom() {
        verifier.verifyThat(TimetableConstraintProvider::onlineTakesNoRoom)
                .given(activity(prof("X"), slot(DayOfWeek.MONDAY, 2), room("A01", 40), true,
                        group("G1", 20)))
                .penalizes(1);
    }

    // ----- what it is still bound by -----

    @Test
    void onlineActivity_stillClashesWithItsOwnGroup() {
        TimeSlot ts = slot(DayOfWeek.MONDAY, 2);
        StudentGroup g = group("G1", 20);
        verifier.verifyThat(TimetableConstraintProvider::noStudentGroupOverlap)
                .given(activity(prof("X"), ts, room("A01", 40), false, g),
                        activity(prof("Y"), ts, null, true, g))
                .penalizes(1);
    }

    @Test
    void onlineActivity_stillClashesWithItsProfessor() {
        TimeSlot ts = slot(DayOfWeek.MONDAY, 2);
        Professor p = prof("Popescu");
        verifier.verifyThat(TimetableConstraintProvider::noProfessorOverlap)
                .given(activity(p, ts, room("A01", 40), false, group("G1", 20)),
                        activity(p, ts, null, true, group("G2", 20)))
                .penalizes(1);
    }

    @Test
    void onlineActivity_stillRespectsProfessorUnavailability() {
        Professor p = prof("Popescu");
        ProfessorUnavailability u = new ProfessorUnavailability();
        u.setProfessor(p);
        u.setDayOfWeek(DayOfWeek.MONDAY); // whole day
        verifier.verifyThat(TimetableConstraintProvider::professorUnavailability)
                .given(u, activity(p, slot(DayOfWeek.MONDAY, 2), null, true, group("G1", 20)))
                .penalizes(1);
    }

    // ----- and the same thing through a real solve -----

    @Test
    void solverPlacesOnlineActivitiesWithoutARoom() {
        List<TimeSlot> slots = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            slots.add(slot(DayOfWeek.MONDAY, i));
        }
        StudentGroup g1 = group("G1", 20);
        StudentGroup g2 = group("G2", 20);
        // one room only: without the online exemption, four activities could never all fit
        List<Room> rooms = List.of(room("A01", 40));
        List<ScheduledActivity> activities = List.of(
                activity(prof("X"), null, null, false, g1),
                activity(prof("Y"), null, null, true, g1),
                activity(prof("Z"), null, null, true, g2),
                activity(prof("W"), null, null, true, g2));

        TimetableSolution problem = new TimetableSolution();
        problem.setTimeSlots(slots);
        problem.setRooms(rooms);
        problem.setStudentGroups(List.of(g1, g2));
        problem.setActivities(new ArrayList<>(activities));

        SolverConfig config = new SolverConfig()
                .withSolutionClass(TimetableSolution.class)
                .withEntityClasses(ScheduledActivity.class)
                .withConstraintProviderClass(TimetableConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(5L));
        TimetableSolution solved = SolverFactory.<TimetableSolution>create(config).buildSolver()
                .solve(problem);

        assertEquals(0, solved.getScore().hardScore(), "hard rules must hold: " + solved.getScore());
        assertEquals(0, solved.getScore().mediumScore(), "everything must be placed: " + solved.getScore());
        for (ScheduledActivity a : solved.getActivities()) {
            assertNotNull(a.getTimeSlot(), "every activity gets an hour");
            assertTrue(a.isPlaced());
            if (a.isOnline()) {
                assertNull(a.getRoom(), "an online activity must not take a room");
            } else {
                assertNotNull(a.getRoom(), "an on-site activity still needs a room");
            }
        }
        // G1's two hours are its own, so they must not land on the same module
        ScheduledActivity g1OnSite = solved.getActivities().stream()
                .filter(a -> !a.isOnline()).findFirst().orElseThrow();
        ScheduledActivity g1Online = solved.getActivities().stream()
                .filter(a -> a.isOnline() && a.getStudentGroups().contains(g1)).findFirst().orElseThrow();
        assertTrue(!g1OnSite.getTimeSlot().getId().equals(g1Online.getTimeSlot().getId()),
                "the same students cannot be in two places at once");
    }
}
