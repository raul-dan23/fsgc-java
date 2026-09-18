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
 * "Must be held in a lab" narrows an activity to the lab rooms. The rule is one-way on purpose:
 * an hour that does not need a lab may still be taught in one when a lab happens to be free.
 */
class LabRequiredTest {

    private final ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> verifier =
            ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class,
                    ScheduledActivity.class);

    private static Room room(RoomTypology typology) {
        Room r = new Room();
        r.setId(1L);
        r.setName(typology == RoomTypology.LAB ? "521" : "028");
        r.setCapacity(40);
        r.setTypology(typology);
        return r;
    }

    private static ScheduledActivity activity(boolean requiresLab, Room room) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(2L);
        Subject s = new Subject();
        s.setId(3L);
        s.setCode("C1");
        s.setName("Retele");
        a.setSubject(s);
        a.setActivityType(ActivityType.LAB);
        a.setRequiresLab(requiresLab);
        a.setWeekParity(WeekParity.EVERY_WEEK);
        a.setTimeSlot(new TimeSlot(11L, DayOfWeek.MONDAY, 1,
                LocalTime.of(8, 0), LocalTime.of(9, 30)));
        a.setRoom(room);
        StudentGroup g = new StudentGroup();
        g.setId(4L);
        g.setName("G1");
        g.setStudentCount(25);
        g.setStudyProgram(StudyProgram.LICENSE);
        a.setStudentGroups(Set.of(g));
        return a;
    }

    @Test
    void anHourThatNeedsALabMayNotSitInASeminarRoom() {
        verifier.verifyThat(TimetableConstraintProvider::labRequired)
                .given(activity(true, room(RoomTypology.SEMINAR)))
                .penalizes(1);
    }

    @Test
    void norInAnAmphitheatre() {
        verifier.verifyThat(TimetableConstraintProvider::labRequired)
                .given(activity(true, room(RoomTypology.AMPHITHEATER)))
                .penalizes(1);
    }

    @Test
    void inALabItIsFine() {
        verifier.verifyThat(TimetableConstraintProvider::labRequired)
                .given(activity(true, room(RoomTypology.LAB)))
                .penalizes(0);
    }

    @Test
    void anHourThatDoesNotNeedALabGoesAnywhere() {
        verifier.verifyThat(TimetableConstraintProvider::labRequired)
                .given(activity(false, room(RoomTypology.SEMINAR)))
                .penalizes(0);
        verifier.verifyThat(TimetableConstraintProvider::labRequired)
                .given(activity(false, room(RoomTypology.LAB)))
                .penalizes(0);
    }
}
