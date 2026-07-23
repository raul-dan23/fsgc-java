package ro.uvt.fsgc.orar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduledActivityTest {

    private static long nextId = 1L;

    private static StudentGroup group(StudyProgram program, int count) {
        StudentGroup g = new StudentGroup();
        g.setId(nextId++);
        g.setName("G" + nextId);
        g.setSpecialization("SP");
        g.setYear(1);
        g.setStudyProgram(program);
        g.setStudentCount(count);
        return g;
    }

    private static ScheduledActivity activity(WeekParity parity, StudentGroup... groups) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(nextId++);
        a.setWeekParity(parity);
        Subject s = new Subject();
        s.setId(nextId++);
        s.setCode("C" + a.getId());
        s.setName("S" + a.getId());
        a.setSubject(s);
        a.setActivityType(ActivityType.COURSE);
        a.setSpecialCategory(SpecialCategory.NORMAL);
        a.setStudentGroups(Set.of(groups));
        return a;
    }

    // ----- totalStudentCount -----

    @Test
    void totalStudentCount_singleGroup() {
        StudentGroup g = group(StudyProgram.LICENSE, 30);
        assertThat(activity(WeekParity.EVERY_WEEK, g).totalStudentCount()).isEqualTo(30);
    }

    @Test
    void totalStudentCount_multipleGroups_summed() {
        StudentGroup g1 = group(StudyProgram.LICENSE, 25);
        StudentGroup g2 = group(StudyProgram.LICENSE, 35);
        assertThat(activity(WeekParity.EVERY_WEEK, g1, g2).totalStudentCount()).isEqualTo(60);
    }

    // ----- isMaster -----

    @Test
    void isMaster_masterGroup_returnsTrue() {
        assertThat(activity(WeekParity.EVERY_WEEK, group(StudyProgram.MASTER, 20)).isMaster()).isTrue();
    }

    @Test
    void isMaster_licenseGroup_returnsFalse() {
        assertThat(activity(WeekParity.EVERY_WEEK, group(StudyProgram.LICENSE, 20)).isMaster()).isFalse();
    }

    @Test
    void isMaster_mixedGroups_returnsTrue() {
        StudentGroup license = group(StudyProgram.LICENSE, 20);
        StudentGroup master = group(StudyProgram.MASTER, 15);
        assertThat(activity(WeekParity.EVERY_WEEK, license, master).isMaster()).isTrue();
    }

    // ----- parityClashesWith -----

    @Test
    void parityClashes_everyWeekVsEveryWeek_clashes() {
        ScheduledActivity a = activity(WeekParity.EVERY_WEEK, group(StudyProgram.LICENSE, 20));
        ScheduledActivity b = activity(WeekParity.EVERY_WEEK, group(StudyProgram.LICENSE, 20));
        assertThat(a.parityClashesWith(b)).isTrue();
    }

    @Test
    void parityClashes_everyWeekVsOdd_clashes() {
        ScheduledActivity a = activity(WeekParity.EVERY_WEEK, group(StudyProgram.LICENSE, 20));
        ScheduledActivity b = activity(WeekParity.ODD_WEEKS, group(StudyProgram.LICENSE, 20));
        assertThat(a.parityClashesWith(b)).isTrue();
    }

    @Test
    void parityClashes_oddVsEven_doesNotClash() {
        ScheduledActivity a = activity(WeekParity.ODD_WEEKS, group(StudyProgram.LICENSE, 20));
        ScheduledActivity b = activity(WeekParity.EVEN_WEEKS, group(StudyProgram.LICENSE, 20));
        assertThat(a.parityClashesWith(b)).isFalse();
    }

    @Test
    void parityClashes_oddVsOdd_clashes() {
        ScheduledActivity a = activity(WeekParity.ODD_WEEKS, group(StudyProgram.LICENSE, 20));
        ScheduledActivity b = activity(WeekParity.ODD_WEEKS, group(StudyProgram.LICENSE, 20));
        assertThat(a.parityClashesWith(b)).isTrue();
    }

    @Test
    void parityClashes_evenVsEven_clashes() {
        ScheduledActivity a = activity(WeekParity.EVEN_WEEKS, group(StudyProgram.LICENSE, 20));
        ScheduledActivity b = activity(WeekParity.EVEN_WEEKS, group(StudyProgram.LICENSE, 20));
        assertThat(a.parityClashesWith(b)).isTrue();
    }
}
