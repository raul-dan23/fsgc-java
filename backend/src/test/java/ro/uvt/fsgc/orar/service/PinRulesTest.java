package ro.uvt.fsgc.orar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
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
import ro.uvt.fsgc.orar.repository.ProfessorRoomRestrictionRepository;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.SpecialBlockRuleRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

/**
 * A pin only means something for an hour that has a place. Pinning an unplaced one would tell the
 * solver to leave it unplaced for good, and taking a pinned hour off the grid releases it.
 */
class PinRulesTest {

    private final ScheduledActivityRepository activityRepo = mock(ScheduledActivityRepository.class);
    private final TimeSlotRepository timeSlotRepo = mock(TimeSlotRepository.class);
    private final RoomRepository roomRepo = mock(RoomRepository.class);
    private final SpecialBlockRuleRepository blockRepo = mock(SpecialBlockRuleRepository.class);
    private final ScheduleService service =
            new ScheduleService(activityRepo, timeSlotRepo, roomRepo, blockRepo,
                    mock(ProfessorRoomRestrictionRepository.class));

    private static final TimeSlot MON1 = new TimeSlot(11L, DayOfWeek.MONDAY, 1,
            LocalTime.of(8, 0), LocalTime.of(9, 30));

    private static ScheduledActivity activity(boolean placed) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(1L);
        Subject s = new Subject();
        s.setCode("C1");
        s.setName("Statistica");
        a.setSubject(s);
        a.setActivityType(ActivityType.SEMINAR);
        a.setWeekParity(WeekParity.EVERY_WEEK);
        StudentGroup g = new StudentGroup();
        g.setId(2L);
        g.setName("G1");
        g.setStudentCount(20);
        g.setStudyProgram(StudyProgram.LICENSE);
        a.setStudentGroups(Set.of(g));
        if (placed) {
            a.setTimeSlot(MON1);
            Room r = new Room();
            r.setId(3L);
            r.setName("028");
            r.setCapacity(30);
            r.setTypology(RoomTypology.SEMINAR);
            a.setRoom(r);
        }
        return a;
    }

    private void given(ScheduledActivity a) {
        when(activityRepo.findById(a.getId())).thenReturn(Optional.of(a));
        when(activityRepo.save(any(ScheduledActivity.class))).thenAnswer(i -> i.getArgument(0));
        when(activityRepo.findAll()).thenReturn(List.of(a));
        when(blockRepo.findAll()).thenReturn(List.of());
    }

    @Test
    void aPlacedHourCanBePinned() {
        ScheduledActivity a = activity(true);
        given(a);
        assertThat(service.setPinned(1L, true).activity().pinned()).isTrue();
        assertThat(a.isPinned()).isTrue();
    }

    @Test
    void anUnplacedHourCannotBe() {
        ScheduledActivity a = activity(false);
        given(a);
        assertThatThrownBy(() -> service.setPinned(1L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("întâi pusă în orar");
        assertThat(a.isPinned()).isFalse();
    }

    @Test
    void takingAPinnedHourOffTheGridReleasesIt() {
        ScheduledActivity a = activity(true);
        a.setPinned(true);
        given(a);
        when(timeSlotRepo.findById(11L)).thenReturn(Optional.of(MON1));

        service.move(1L, null, null);

        assertThat(a.isPinned()).as("an hour with no place cannot stay fixed to it").isFalse();
    }

    @Test
    void movingAPinnedHourKeepsItPinnedAtItsNewPlace() {
        ScheduledActivity a = activity(true);
        a.setPinned(true);
        given(a);
        when(timeSlotRepo.findById(11L)).thenReturn(Optional.of(MON1));
        when(roomRepo.findById(3L)).thenReturn(Optional.of(a.getRoom()));

        service.move(1L, 11L, 3L);

        assertThat(a.isPinned()).isTrue();
    }
}
