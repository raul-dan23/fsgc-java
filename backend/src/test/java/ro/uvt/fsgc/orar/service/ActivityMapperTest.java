package ro.uvt.fsgc.orar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.domain.WeekParity;
import ro.uvt.fsgc.orar.dto.ActivityView;

class ActivityMapperTest {

    // ------------------------------------------------------------------ helpers

    private Subject makeSubject(String code, String name) {
        Subject s = new Subject();
        s.setCode(code);
        s.setName(name);
        return s;
    }

    private Professor makeProfessor(String name) {
        Professor p = new Professor();
        p.setName(name);
        return p;
    }

    private Room makeRoom(String name) {
        Room r = new Room();
        r.setName(name);
        return r;
    }

    private TimeSlot makeSlot(long id, DayOfWeek day, int slotIndex, LocalTime start, LocalTime end) {
        return new TimeSlot(id, day, slotIndex, start, end);
    }

    private StudentGroup makeGroup(String name, String specialization, int year,
                                   StudyProgram program, int studentCount) {
        StudentGroup g = new StudentGroup();
        g.setName(name);
        g.setSpecialization(specialization);
        g.setYear(year);
        g.setStudyProgram(program);
        g.setStudentCount(studentCount);
        return g;
    }

    private ScheduledActivity baseActivity() {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(1L);
        a.setSubject(makeSubject("CS101", "Algorithms"));
        a.setProfessor(makeProfessor("Prof. Ion Pop"));
        a.setActivityType(ActivityType.COURSE);
        a.setRawType("Curs");
        a.setWeekParity(WeekParity.EVERY_WEEK);
        a.setSpecialCategory(SpecialCategory.NORMAL);
        StudentGroup g = makeGroup("RISE1-G1", "RISE", 1, StudyProgram.LICENSE, 25);
        a.setStudentGroups(Set.of(g));
        return a;
    }

    // ------------------------------------------------------------------ fully assigned

    @Test
    void toView_fullyAssigned_populatesTimeslotAndRoom() {
        ScheduledActivity a = baseActivity();
        TimeSlot ts = makeSlot(11L, DayOfWeek.MONDAY, 1,
                LocalTime.of(8, 0), LocalTime.of(9, 30));
        a.setTimeSlot(ts);
        Room room = makeRoom("C201");
        a.setRoom(room);

        ActivityView view = ActivityMapper.toView(a);

        assertThat(view.assigned()).isTrue();
        assertThat(view.day()).isEqualTo("MONDAY");
        assertThat(view.slotIndex()).isEqualTo(1);
        assertThat(view.startTime()).isEqualTo("08:00");
        assertThat(view.endTime()).isEqualTo("09:30");
        assertThat(view.room()).isEqualTo("C201");
        assertThat(view.timeSlotId()).isEqualTo(11L);
    }

    // ------------------------------------------------------------------ unassigned

    @Test
    void toView_unassigned_nullTimeslotAndRoom() {
        ScheduledActivity a = baseActivity();
        // timeSlot and room left null (default)

        ActivityView view = ActivityMapper.toView(a);

        assertThat(view.assigned()).isFalse();
        assertThat(view.day()).isNull();
        assertThat(view.slotIndex()).isNull();
        assertThat(view.startTime()).isNull();
        assertThat(view.endTime()).isNull();
        assertThat(view.room()).isNull();
        assertThat(view.timeSlotId()).isNull();
    }

    // ------------------------------------------------------------------ null professor

    @Test
    void toView_nullProfessor_profNameIsNull() {
        ScheduledActivity a = baseActivity();
        a.setProfessor(null);

        ActivityView view = ActivityMapper.toView(a);

        assertThat(view.professor()).isNull();
    }

    // ------------------------------------------------------------------ subject fields

    @Test
    void toView_subjectFieldsMapped() {
        ScheduledActivity a = baseActivity();

        ActivityView view = ActivityMapper.toView(a);

        assertThat(view.subjectCode()).isEqualTo("CS101");
        assertThat(view.subject()).isEqualTo("Algorithms");
    }

    // ------------------------------------------------------------------ combined groups

    @Test
    void toView_combinedGroups_sortedNamesAndSummedStudentCount() {
        ScheduledActivity a = baseActivity();
        StudentGroup g1 = makeGroup("RISE1-G2", "RISE", 1, StudyProgram.LICENSE, 20);
        StudentGroup g2 = makeGroup("AP1-G1",   "AP",   1, StudyProgram.LICENSE, 30);
        // g1 name "RISE1-G2" sorts after "AP1-G1"
        a.setStudentGroups(Set.of(g1, g2));

        ActivityView view = ActivityMapper.toView(a);

        assertThat(view.groups()).containsExactly("AP1-G1", "RISE1-G2");
        assertThat(view.students()).isEqualTo(50);
    }
}
