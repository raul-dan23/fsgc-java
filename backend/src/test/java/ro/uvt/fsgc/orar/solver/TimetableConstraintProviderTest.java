package ro.uvt.fsgc.orar.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.BlockedDayRule;
import ro.uvt.fsgc.orar.domain.Building;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;
import ro.uvt.fsgc.orar.domain.ProfessorUnavailability;
import ro.uvt.fsgc.orar.domain.RestrictionType;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomUnavailability;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialBlockRule;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.domain.WeekParity;

/**
 * One test per hard constraint proving a violation is detected (plus a couple of negative cases).
 * Uses Timefold's {@link ConstraintVerifier}; {@code penalizes(n)} asserts the match count,
 * independent of the configured weight.
 */
class TimetableConstraintProviderTest {

    private final ConstraintVerifier<TimetableConstraintProvider, TimetableSolution> verifier =
            ConstraintVerifier.build(new TimetableConstraintProvider(), TimetableSolution.class,
                    ScheduledActivity.class);

    // ----- builders -----

    private static long ids = 1;

    private static TimeSlot slot(DayOfWeek day, int index) {
        LocalTime[][] times = {
                {LocalTime.of(8, 0), LocalTime.of(9, 30)}, {LocalTime.of(9, 40), LocalTime.of(11, 10)},
                {LocalTime.of(11, 20), LocalTime.of(12, 50)}, {LocalTime.of(13, 0), LocalTime.of(14, 30)},
                {LocalTime.of(14, 40), LocalTime.of(16, 10)}, {LocalTime.of(16, 20), LocalTime.of(17, 50)},
                {LocalTime.of(18, 0), LocalTime.of(19, 30)}, {LocalTime.of(19, 40), LocalTime.of(21, 10)},
        };
        long id = day.getValue() * 10L + index;
        return new TimeSlot(id, day, index, times[index - 1][0], times[index - 1][1]);
    }

    private static StudentGroup group(String name, String spec, int year, StudyProgram p, int size) {
        StudentGroup g = new StudentGroup();
        g.setId(ids++);
        g.setName(name);
        g.setSpecialization(spec);
        g.setYear(year);
        g.setStudyProgram(p);
        g.setStudentCount(size);
        return g;
    }

    private static Room room(String name, int cap, RoomTypology t, Building b) {
        Room r = new Room();
        r.setId(ids++);
        r.setName(name);
        r.setCapacity(cap);
        r.setTypology(t);
        r.setBuilding(b);
        return r;
    }

    private static RoomUnavailability unavailable(Room r, DayOfWeek day, LocalTime from, LocalTime to) {
        RoomUnavailability u = new RoomUnavailability();
        u.setId(ids++);
        u.setRoom(r);
        u.setDayOfWeek(day);
        u.setStartTime(from);
        u.setEndTime(to);
        return u;
    }

    private static Professor prof(String name) {
        Professor p = new Professor();
        p.setId(ids++);
        p.setName(name);
        return p;
    }

    private static ScheduledActivity activity(Professor p, ActivityType type, TimeSlot ts, Room room,
                                              WeekParity parity, StudentGroup... groups) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(ids++);
        Subject s = new Subject();
        s.setId(ids++);
        s.setCode("C" + a.getId());
        s.setName("Subject " + a.getId());
        a.setSubject(s);
        a.setProfessor(p);
        a.setActivityType(type);
        a.setTimeSlot(ts);
        a.setRoom(room);
        a.setWeekParity(parity);
        a.setStudentGroups(Set.of(groups));
        return a;
    }

    // ----- hard constraint tests -----

    @Test
    void noProfessorOverlap_detected() {
        Professor p = prof("X");
        TimeSlot ts = slot(DayOfWeek.MONDAY, 1);
        Room r1 = room("A", 50, RoomTypology.SEMINAR, null);
        Room r2 = room("B", 50, RoomTypology.SEMINAR, null);
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::noProfessorOverlap)
                .given(activity(p, ActivityType.COURSE, ts, r1, WeekParity.EVERY_WEEK, g),
                        activity(p, ActivityType.SEMINAR, ts, r2, WeekParity.EVERY_WEEK, g))
                .penalizes(1);
    }

    @Test
    void noRoomOverlap_detected() {
        TimeSlot ts = slot(DayOfWeek.TUESDAY, 2);
        Room r = room("A", 50, RoomTypology.SEMINAR, null);
        StudentGroup g1 = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        StudentGroup g2 = group("G2", "G", 2, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::noRoomOverlap)
                .given(activity(prof("X"), ActivityType.COURSE, ts, r, WeekParity.EVERY_WEEK, g1),
                        activity(prof("Y"), ActivityType.SEMINAR, ts, r, WeekParity.EVERY_WEEK, g2))
                .penalizes(1);
    }

    @Test
    void noRoomOverlap_differentParity_allowed() {
        TimeSlot ts = slot(DayOfWeek.TUESDAY, 2);
        Room r = room("A", 50, RoomTypology.SEMINAR, null);
        StudentGroup g1 = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        StudentGroup g2 = group("G2", "G", 2, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::noRoomOverlap)
                .given(activity(prof("X"), ActivityType.COURSE, ts, r, WeekParity.ODD_WEEKS, g1),
                        activity(prof("Y"), ActivityType.SEMINAR, ts, r, WeekParity.EVEN_WEEKS, g2))
                .penalizes(0);
    }

    @Test
    void noStudentGroupOverlap_detected() {
        TimeSlot ts = slot(DayOfWeek.WEDNESDAY, 3);
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::noStudentGroupOverlap)
                .given(activity(prof("X"), ActivityType.COURSE, ts,
                                room("A", 50, RoomTypology.SEMINAR, null), WeekParity.EVERY_WEEK, g),
                        activity(prof("Y"), ActivityType.SEMINAR, ts,
                                room("B", 50, RoomTypology.SEMINAR, null), WeekParity.EVERY_WEEK, g))
                .penalizes(1);
    }

    @Test
    void roomCapacity_detected() {
        StudentGroup big = group("Big", "G", 1, StudyProgram.LICENSE, 60);
        verifier.verifyThat(TimetableConstraintProvider::roomCapacity)
                .given(activity(prof("X"), ActivityType.COURSE, slot(DayOfWeek.MONDAY, 1),
                        room("Small", 20, RoomTypology.SEMINAR, null), WeekParity.EVERY_WEEK, big))
                .penalizes(1);
    }

    @Test
    void amphitheaterRequired_detected() {
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 100);
        ScheduledActivity a = activity(prof("X"), ActivityType.COURSE, slot(DayOfWeek.MONDAY, 1),
                room("Sem", 120, RoomTypology.SEMINAR, null), WeekParity.EVERY_WEEK, g);
        a.setRequiresAmphitheater(true);
        verifier.verifyThat(TimetableConstraintProvider::roomTypeMatchesActivity).given(a).penalizes(1);
    }

    @Test
    void roomUnavailability_detected() {
        Room r = room("A", 50, RoomTypology.SEMINAR, null);
        // blocked Monday morning; the activity sits right inside that window
        r.getUnavailabilities().add(unavailable(r, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(9, 30)));
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::roomUnavailability)
                .given(activity(prof("X"), ActivityType.COURSE, slot(DayOfWeek.MONDAY, 1), r,
                        WeekParity.EVERY_WEEK, g))
                .penalizes(1);
    }

    @Test
    void roomUnavailability_partialOverlapStillBlocks() {
        Room r = room("A", 50, RoomTypology.SEMINAR, null);
        // window clips only the tail of module 1 (08:00-09:30): an overlap is still a clash
        r.getUnavailabilities().add(unavailable(r, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::roomUnavailability)
                .given(activity(prof("X"), ActivityType.COURSE, slot(DayOfWeek.MONDAY, 1), r,
                        WeekParity.EVERY_WEEK, g))
                .penalizes(1);
    }

    @Test
    void roomWithoutUnavailability_isUsableOnEveryModule() {
        // the point of the inverted model: no rows means the room is free all week, including
        // module 8 (19:40-21:10), which the old availability windows used to exclude
        Room r = room("A", 50, RoomTypology.SEMINAR, null);
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::roomUnavailability)
                .given(activity(prof("X"), ActivityType.COURSE, slot(DayOfWeek.FRIDAY, 8), r,
                        WeekParity.EVERY_WEEK, g))
                .penalizes(0);
    }

    @Test
    void roomUnavailability_otherDayIsUnaffected() {
        Room r = room("A", 50, RoomTypology.SEMINAR, null);
        r.getUnavailabilities().add(unavailable(r, DayOfWeek.MONDAY,
                LocalTime.of(8, 0), LocalTime.of(21, 10)));
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::roomUnavailability)
                .given(activity(prof("X"), ActivityType.COURSE, slot(DayOfWeek.TUESDAY, 1), r,
                        WeekParity.EVERY_WEEK, g))
                .penalizes(0);
    }

    @Test
    void masterEveningOnly_detected() {
        StudentGroup m = group("M1", "MPA", 1, StudyProgram.MASTER, 20);
        verifier.verifyThat(TimetableConstraintProvider::masterEveningOnly)
                .given(activity(prof("X"), ActivityType.COURSE, slot(DayOfWeek.MONDAY, 3),
                        room("A", 50, RoomTypology.SEMINAR, null), WeekParity.EVERY_WEEK, m))
                .penalizes(1);
    }

    @Test
    void blockedDayForTerminalYear_detected() {
        StudentGroup y3 = group("SP3", "SP", 3, StudyProgram.LICENSE, 12);
        BlockedDayRule rule = new BlockedDayRule();
        rule.setStudyProgram(StudyProgram.LICENSE);
        rule.setYear(3);
        rule.setDayOfWeek(DayOfWeek.FRIDAY);
        verifier.verifyThat(TimetableConstraintProvider::blockedDayForTerminalYear)
                .given(rule, activity(prof("X"), ActivityType.SEMINAR, slot(DayOfWeek.FRIDAY, 2),
                        room("A", 50, RoomTypology.SEMINAR, null), WeekParity.EVERY_WEEK, y3))
                .penalizes(1);
    }

    @Test
    void specialCategoryBlock_detected() {
        TimeSlot ts = slot(DayOfWeek.THURSDAY, 5);
        StudentGroup y1 = group("AP1", "AP", 1, StudyProgram.LICENSE, 40);
        SpecialBlockRule rule = new SpecialBlockRule();
        rule.setStudentGroup(y1);
        rule.setTimeSlot(ts);
        rule.setCategory(SpecialCategory.DPPD);
        verifier.verifyThat(TimetableConstraintProvider::specialCategoryBlock)
                .given(rule, activity(prof("X"), ActivityType.SEMINAR, ts,
                        room("A", 50, RoomTypology.SEMINAR, null), WeekParity.EVERY_WEEK, y1))
                .penalizes(1);
    }

    @Test
    void professorUnavailability_detected() {
        Professor p = prof("Popescu");
        ProfessorUnavailability u = new ProfessorUnavailability();
        u.setProfessor(p);
        u.setDayOfWeek(DayOfWeek.MONDAY); // whole day blocked (no times)
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::professorUnavailability)
                .given(u, activity(p, ActivityType.SEMINAR, slot(DayOfWeek.MONDAY, 2),
                        room("A", 50, RoomTypology.SEMINAR, null), WeekParity.EVERY_WEEK, g))
                .penalizes(1);
    }

    @Test
    void professorForbiddenRoom_detected() {
        Professor p = prof("Popescu");
        Room forbidden = room("P01", 50, RoomTypology.SEMINAR, null);
        ProfessorRoomRestriction r = new ProfessorRoomRestriction();
        r.setProfessor(p);
        r.setRoom(forbidden);
        r.setRestrictionType(RestrictionType.FORBIDDEN);
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        verifier.verifyThat(TimetableConstraintProvider::professorRoomForbidden)
                .given(r, activity(p, ActivityType.SEMINAR, slot(DayOfWeek.MONDAY, 2), forbidden,
                        WeekParity.EVERY_WEEK, g))
                .penalizes(1);
    }

    @Test
    void professorOnlyThisRoom_detected() {
        Professor p = prof("Popescu");
        Room allowed = room("Paris1", 50, RoomTypology.SEMINAR, null);
        Room other = room("Parvan1", 50, RoomTypology.SEMINAR, null);
        ProfessorRoomRestriction r = new ProfessorRoomRestriction();
        r.setProfessor(p);
        r.setRoom(allowed);
        r.setRestrictionType(RestrictionType.ONLY_THIS);
        StudentGroup g = group("G1", "G", 1, StudyProgram.LICENSE, 20);
        // teaching in 'other' while only 'allowed' is whitelisted -> violation
        verifier.verifyThat(TimetableConstraintProvider::professorRoomOnlyThis)
                .given(r, activity(p, ActivityType.SEMINAR, slot(DayOfWeek.MONDAY, 2), other,
                        WeekParity.EVERY_WEEK, g))
                .penalizes(1);
    }

    @Test
    void consecutiveSlotsSameBuilding_detected() {
        Building paris = new Building("Paris", "Str. Paris");
        Building parvan = new Building("Parvan", "Bd. Parvan");
        StudentGroup g = group("AP3", "AP", 3, StudyProgram.LICENSE, 24);
        verifier.verifyThat(TimetableConstraintProvider::consecutiveSlotsSameBuilding)
                .given(activity(prof("X"), ActivityType.COURSE, slot(DayOfWeek.MONDAY, 1),
                                room("P01", 50, RoomTypology.SEMINAR, paris), WeekParity.EVERY_WEEK, g),
                        activity(prof("Y"), ActivityType.SEMINAR, slot(DayOfWeek.MONDAY, 2),
                                room("V01", 50, RoomTypology.SEMINAR, parvan), WeekParity.EVERY_WEEK, g))
                .penalizes(1);
    }
}
