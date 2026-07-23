package ro.uvt.fsgc.orar.solver;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.WeekParity;

class ActivityDifficultyComparatorTest {

    private final ActivityDifficultyComparator comparator = new ActivityDifficultyComparator();

    private static long nextId = 1000L;

    private static StudentGroup makeGroup(int studentCount) {
        StudentGroup g = new StudentGroup();
        g.setId(nextId++);
        g.setStudentCount(studentCount);
        g.setName("G" + nextId);
        g.setSpecialization("SP");
        g.setYear(1);
        g.setStudyProgram(StudyProgram.LICENSE);
        return g;
    }

    private static ScheduledActivity makeActivity(boolean requiresAmphitheater, int... studentCounts) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(nextId++);
        a.setRequiresAmphitheater(requiresAmphitheater);
        a.setActivityType(ActivityType.COURSE);
        a.setWeekParity(WeekParity.EVERY_WEEK);
        a.setSpecialCategory(SpecialCategory.NORMAL);
        Subject s = new Subject();
        s.setId(nextId++);
        s.setCode("C" + a.getId());
        s.setName("Subj" + a.getId());
        a.setSubject(s);
        Set<StudentGroup> groups = new java.util.HashSet<>();
        for (int count : studentCounts) {
            groups.add(makeGroup(count));
        }
        a.setStudentGroups(groups);
        return a;
    }

    @Test
    void amphitheaterActivityComesBeforeNormal() {
        // reversed() → amphi (score=1) reversed to smaller → comes first in ascending sort
        ScheduledActivity amphi = makeActivity(true, 30);
        ScheduledActivity normal = makeActivity(false, 30);
        assertThat(comparator.compare(amphi, normal)).isNegative();
        assertThat(comparator.compare(normal, amphi)).isPositive();
    }

    @Test
    void largerStudentCountComesFirst() {
        ScheduledActivity big = makeActivity(false, 100);
        ScheduledActivity small = makeActivity(false, 20);
        assertThat(comparator.compare(big, small)).isNegative();
        assertThat(comparator.compare(small, big)).isPositive();
    }

    @Test
    void amphitheaterTakesPriorityOverStudentCount() {
        ScheduledActivity amphiSmall = makeActivity(true, 10);
        ScheduledActivity normalBig = makeActivity(false, 200);
        assertThat(comparator.compare(amphiSmall, normalBig)).isNegative();
    }

    @Test
    void moreGroupsComesFirst_whenOtherFieldsEqual() {
        ScheduledActivity twoGroups = makeActivity(false, 30, 30);
        ScheduledActivity oneGroup = makeActivity(false, 60);
        assertThat(comparator.compare(twoGroups, oneGroup)).isNegative();
    }

    @Test
    void equalActivitiesCompareToZero() {
        ScheduledActivity a = makeActivity(false, 30);
        ScheduledActivity b = makeActivity(false, 30);
        assertThat(comparator.compare(a, b)).isZero();
    }
}
