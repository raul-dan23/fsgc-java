package ro.uvt.fsgc.orar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * A room taken every other week — A11 on Wednesday afternoons in even weeks — must not be blocked
 * for the whole term. Blocking it always would throw away one of the two amphitheatres for half
 * the semester; blocking it never would book a room that is occupied.
 */
class RoomUnavailabilityParityTest {

    private static final LocalTime FROM = LocalTime.of(16, 20);
    private static final LocalTime TO = LocalTime.of(17, 50);

    private static RoomUnavailability window(WeekParity parity) {
        RoomUnavailability u = new RoomUnavailability();
        u.setDayOfWeek(DayOfWeek.WEDNESDAY);
        u.setStartTime(FROM);
        u.setEndTime(TO);
        u.setWeekParity(parity);
        return u;
    }

    @Test
    void anEveryWeekWindowBlocksEveryHour() {
        RoomUnavailability u = window(WeekParity.EVERY_WEEK);
        assertThat(u.blocks(DayOfWeek.WEDNESDAY, FROM, TO, WeekParity.EVERY_WEEK)).isTrue();
        assertThat(u.blocks(DayOfWeek.WEDNESDAY, FROM, TO, WeekParity.ODD_WEEKS)).isTrue();
        assertThat(u.blocks(DayOfWeek.WEDNESDAY, FROM, TO, WeekParity.EVEN_WEEKS)).isTrue();
    }

    @Test
    void anEvenWeekWindowLeavesOddWeeksAlone() {
        RoomUnavailability u = window(WeekParity.EVEN_WEEKS);
        assertThat(u.blocks(DayOfWeek.WEDNESDAY, FROM, TO, WeekParity.EVEN_WEEKS)).isTrue();
        assertThat(u.blocks(DayOfWeek.WEDNESDAY, FROM, TO, WeekParity.ODD_WEEKS)).isFalse();
        // an hour held every week runs in even weeks too, so it does clash
        assertThat(u.blocks(DayOfWeek.WEDNESDAY, FROM, TO, WeekParity.EVERY_WEEK)).isTrue();
    }

    @Test
    void anotherDayOrHourIsNeverBlocked() {
        RoomUnavailability u = window(WeekParity.EVERY_WEEK);
        assertThat(u.blocks(DayOfWeek.THURSDAY, FROM, TO, WeekParity.EVERY_WEEK)).isFalse();
        assertThat(u.blocks(DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(19, 30),
                WeekParity.EVERY_WEEK)).isFalse();
    }
}
