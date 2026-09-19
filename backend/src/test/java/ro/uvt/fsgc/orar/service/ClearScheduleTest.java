package ro.uvt.fsgc.orar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
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
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.SpecialBlockRuleRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

/**
 * Emptying the grid takes the placement away and nothing else: the hours themselves survive, with
 * everything that says what they are — which is the whole point of starting the puzzle again.
 */
class ClearScheduleTest {

    private final ScheduledActivityRepository activityRepo = mock(ScheduledActivityRepository.class);
    private final ScheduleService service = new ScheduleService(activityRepo,
            mock(TimeSlotRepository.class), mock(RoomRepository.class),
            mock(SpecialBlockRuleRepository.class));

    private static ScheduledActivity activity(long id, boolean placed, boolean pinned) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(id);
        Subject s = new Subject();
        s.setCode("C" + id);
        s.setName("Materia " + id);
        a.setSubject(s);
        a.setActivityType(ActivityType.SEMINAR);
        a.setWeekParity(WeekParity.EVERY_WEEK);
        a.setOnline(id == 3);
        a.setRequiresLab(id == 2);
        StudentGroup g = new StudentGroup();
        g.setId(100 + id);
        g.setName("G" + id);
        g.setStudentCount(20);
        g.setStudyProgram(StudyProgram.LICENSE);
        a.setStudentGroups(Set.of(g));
        if (placed) {
            a.setTimeSlot(new TimeSlot(11L, DayOfWeek.MONDAY, 1,
                    LocalTime.of(8, 0), LocalTime.of(9, 30)));
            if (!a.isOnline()) {
                Room r = new Room();
                r.setId(9L);
                r.setName("028");
                r.setCapacity(30);
                r.setTypology(RoomTypology.SEMINAR);
                a.setRoom(r);
            }
            a.setPinned(pinned);
        }
        return a;
    }

    @Test
    void everyHourGoesBackToUnplaced() {
        List<ScheduledActivity> all = List.of(
                activity(1, true, false), activity(2, true, true),
                activity(3, true, false), activity(4, false, false));
        when(activityRepo.findAll()).thenReturn(all);
        when(activityRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        ScheduleService.ClearResult res = service.clearSchedule();

        assertThat(res.total()).isEqualTo(4);
        assertThat(res.cleared()).as("the one already unplaced is not counted").isEqualTo(3);
        assertThat(all).allSatisfy(a -> {
            assertThat(a.getTimeSlot()).isNull();
            assertThat(a.getRoom()).isNull();
            assertThat(a.isPinned()).as("an hour with no place cannot stay fixed").isFalse();
            assertThat(a.isPlaced()).isFalse();
        });
    }

    @Test
    void whatTheHourIsStaysUntouched() {
        ScheduledActivity online = activity(3, true, false);
        ScheduledActivity lab = activity(2, true, true);
        when(activityRepo.findAll()).thenReturn(List.of(online, lab));
        when(activityRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        service.clearSchedule();

        assertThat(online.isOnline()).isTrue();
        assertThat(lab.isRequiresLab()).isTrue();
        assertThat(lab.getSubject().getName()).isEqualTo("Materia 2");
        assertThat(lab.getStudentGroups()).hasSize(1);
        assertThat(lab.getActivityType()).isEqualTo(ActivityType.SEMINAR);
    }
}
