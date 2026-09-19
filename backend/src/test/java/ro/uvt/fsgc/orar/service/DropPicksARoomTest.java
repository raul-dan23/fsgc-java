package ro.uvt.fsgc.orar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;
import ro.uvt.fsgc.orar.domain.RestrictionType;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.RoomUnavailability;
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
 * The grid on sections has no room column: an hour is dropped on a day and a module, and a room
 * has to be found for it. Without one the hour would keep its slot, stay room-less and count as
 * unplaced — it would disappear from the grid the moment it was dropped.
 */
class DropPicksARoomTest {

    private final ScheduledActivityRepository activityRepo = mock(ScheduledActivityRepository.class);
    private final TimeSlotRepository timeSlotRepo = mock(TimeSlotRepository.class);
    private final RoomRepository roomRepo = mock(RoomRepository.class);
    private final ProfessorRoomRestrictionRepository profRoomRepo =
            mock(ProfessorRoomRestrictionRepository.class);
    private final ScheduleService service = new ScheduleService(activityRepo, timeSlotRepo,
            roomRepo, mock(SpecialBlockRuleRepository.class), profRoomRepo);

    private static final TimeSlot MON1 = new TimeSlot(11L, DayOfWeek.MONDAY, 1,
            LocalTime.of(8, 0), LocalTime.of(9, 30));

    private static Room room(long id, String name, int capacity, RoomTypology t) {
        Room r = new Room();
        r.setId(id);
        r.setName(name);
        r.setCapacity(capacity);
        r.setTypology(t);
        return r;
    }

    private static ScheduledActivity activity(long id, int students) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(id);
        Subject s = new Subject();
        s.setCode("C" + id);
        s.setName("Materia " + id);
        a.setSubject(s);
        a.setActivityType(ActivityType.SEMINAR);
        a.setWeekParity(WeekParity.EVERY_WEEK);
        StudentGroup g = new StudentGroup();
        g.setId(100 + id);
        g.setName("G" + id);
        g.setStudentCount(students);
        g.setStudyProgram(StudyProgram.LICENSE);
        a.setStudentGroups(Set.of(g));
        return a;
    }

    private void world(List<Room> rooms, ScheduledActivity dropped, ScheduledActivity... others) {
        List<ScheduledActivity> all = new ArrayList<>(List.of(others));
        all.add(dropped);
        when(activityRepo.findById(dropped.getId())).thenReturn(Optional.of(dropped));
        when(activityRepo.findAll()).thenReturn(all);
        when(activityRepo.save(any(ScheduledActivity.class))).thenAnswer(i -> i.getArgument(0));
        when(timeSlotRepo.findById(11L)).thenReturn(Optional.of(MON1));
        when(roomRepo.findAll()).thenReturn(rooms);
        when(profRoomRepo.findAll()).thenReturn(List.of());
    }

    @Test
    void anHourDroppedWithoutARoomGetsOneAndCountsAsPlaced() {
        ScheduledActivity a = activity(1, 25);
        world(List.of(room(1, "A03", 136, RoomTypology.AMPHITHEATER),
                room(2, "028", 30, RoomTypology.SEMINAR)), a);

        ScheduleService.MoveResult res = service.move(1L, 11L, null);

        assertThat(res.activity().room()).isEqualTo("028");
        assertThat(res.activity().assigned()).as("this is what kept failing").isTrue();
        assertThat(res.violations()).isEmpty();
    }

    @Test
    void theSmallestRoomThatFitsWins() {
        ScheduledActivity a = activity(1, 31);
        world(List.of(room(1, "A03", 136, RoomTypology.AMPHITHEATER),
                room(2, "028", 30, RoomTypology.SEMINAR),
                room(3, "130", 50, RoomTypology.SEMINAR)), a);

        assertThat(service.move(1L, 11L, null).activity().room())
                .as("028 is too small, A03 wastes a hundred seats").isEqualTo("130");
    }

    @Test
    void aRoomTakenAtThatHourIsSkipped() {
        ScheduledActivity a = activity(1, 25);
        ScheduledActivity busy = activity(2, 25);
        busy.setTimeSlot(MON1);
        busy.setRoom(room(2, "028", 30, RoomTypology.SEMINAR));
        world(List.of(room(2, "028", 30, RoomTypology.SEMINAR),
                room(3, "130", 50, RoomTypology.SEMINAR)), a, busy);

        assertThat(service.move(1L, 11L, null).activity().room()).isEqualTo("130");
    }

    @Test
    void soIsARoomMarkedUnavailableThen() {
        ScheduledActivity a = activity(1, 25);
        Room closed = room(2, "028", 30, RoomTypology.SEMINAR);
        RoomUnavailability u = new RoomUnavailability();
        u.setRoom(closed);
        u.setDayOfWeek(DayOfWeek.MONDAY);
        u.setStartTime(LocalTime.of(8, 0));
        u.setEndTime(LocalTime.of(21, 10));
        closed.getUnavailabilities().add(u);
        world(List.of(closed, room(3, "130", 50, RoomTypology.SEMINAR)), a);

        assertThat(service.move(1L, 11L, null).activity().room()).isEqualTo("130");
    }

    @Test
    void anHourThatNeedsALabGetsALab() {
        ScheduledActivity a = activity(1, 25);
        a.setRequiresLab(true);
        world(List.of(room(2, "028", 30, RoomTypology.SEMINAR),
                room(4, "521", 33, RoomTypology.LAB)), a);

        assertThat(service.move(1L, 11L, null).activity().room()).isEqualTo("521");
    }

    @Test
    void aRoomTheProfessorMayNotUseIsNotChosen() {
        // the bug this was written for: an hour of someone barred from P01 landed in P01
        Professor prof = new Professor();
        prof.setId(7L);
        prof.setName("Gencia");
        ScheduledActivity a = activity(1, 20);
        a.setProfessor(prof);
        Room p01 = room(5, "P01", 26, RoomTypology.SEMINAR);
        Room r130 = room(3, "130", 50, RoomTypology.SEMINAR);
        world(List.of(p01, r130), a);
        ProfessorRoomRestriction no = new ProfessorRoomRestriction();
        no.setProfessor(prof);
        no.setRoom(p01);
        no.setRestrictionType(RestrictionType.FORBIDDEN);
        when(profRoomRepo.findAll()).thenReturn(List.of(no));

        ScheduleService.MoveResult res = service.move(1L, 11L, null);

        assertThat(res.activity().room()).as("P01 is the smallest, but it is barred").isEqualTo("130");
        assertThat(res.violations()).isEmpty();
    }

    @Test
    void aWhitelistNarrowsTheChoiceToItsRooms() {
        Professor prof = new Professor();
        prof.setId(8L);
        prof.setName("Popescu");
        ScheduledActivity a = activity(1, 20);
        a.setProfessor(prof);
        Room small = room(2, "028", 30, RoomTypology.SEMINAR);
        Room only = room(3, "130", 50, RoomTypology.SEMINAR);
        world(List.of(small, only), a);
        ProfessorRoomRestriction yes = new ProfessorRoomRestriction();
        yes.setProfessor(prof);
        yes.setRoom(only);
        yes.setRestrictionType(RestrictionType.ONLY_THIS);
        when(profRoomRepo.findAll()).thenReturn(List.of(yes));

        assertThat(service.move(1L, 11L, null).activity().room()).isEqualTo("130");
    }

    @Test
    void theOfferedRoomsSayWhyTheOthersDoNotFit() {
        Professor prof = new Professor();
        prof.setId(9L);
        prof.setName("Gencia");
        ScheduledActivity a = activity(1, 20);
        a.setProfessor(prof);
        Room p01 = room(5, "P01", 26, RoomTypology.SEMINAR);
        Room tiny = room(6, "518", 10, RoomTypology.SEMINAR);
        Room ok = room(3, "130", 50, RoomTypology.SEMINAR);
        a.setTimeSlot(MON1);
        world(List.of(p01, tiny, ok), a);
        ProfessorRoomRestriction no = new ProfessorRoomRestriction();
        no.setProfessor(prof);
        no.setRoom(p01);
        no.setRestrictionType(RestrictionType.FORBIDDEN);
        when(profRoomRepo.findAll()).thenReturn(List.of(no));

        List<ScheduleService.RoomOption> options = service.roomOptions(1L, 11L);

        assertThat(options).extracting(ScheduleService.RoomOption::name)
                .containsExactly("130", "518", "P01");
        assertThat(options).filteredOn(ScheduleService.RoomOption::usable)
                .extracting(ScheduleService.RoomOption::name).containsExactly("130");
        assertThat(options).filteredOn(o -> "P01".equals(o.name())).first()
                .extracting(ScheduleService.RoomOption::reason)
                .asString().contains("interzisă pentru Gencia");
        assertThat(options).filteredOn(o -> "518".equals(o.name())).first()
                .extracting(ScheduleService.RoomOption::reason)
                .asString().contains("prea mică");
    }

    @Test
    void anOnlineHourStillTakesNoRoom() {
        ScheduledActivity a = activity(1, 25);
        a.setOnline(true);
        world(List.of(room(2, "028", 30, RoomTypology.SEMINAR)), a);

        ScheduleService.MoveResult res = service.move(1L, 11L, null);
        assertThat(res.activity().room()).isNull();
        assertThat(res.activity().assigned()).isTrue();
    }

    @Test
    void whenNothingIsFreeItStillLandsSomewhereAndSaysWhatIsWrong() {
        ScheduledActivity a = activity(1, 25);
        ScheduledActivity busy = activity(2, 25);
        busy.setTimeSlot(MON1);
        busy.setRoom(room(2, "028", 30, RoomTypology.SEMINAR));
        world(List.of(room(2, "028", 30, RoomTypology.SEMINAR)), a, busy);

        ScheduleService.MoveResult res = service.move(1L, 11L, null);

        assertThat(res.activity().room()).isEqualTo("028");
        assertThat(res.activity().assigned()).isTrue();
        assertThat(res.violations()).anyMatch(v -> v.startsWith("Room clash"));
    }
}
