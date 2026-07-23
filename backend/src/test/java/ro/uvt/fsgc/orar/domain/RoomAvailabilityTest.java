package ro.uvt.fsgc.orar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class RoomAvailabilityTest {

    // Standard window used across most tests: MONDAY 08:00–20:00
    private static final DayOfWeek DAY   = DayOfWeek.MONDAY;
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END   = LocalTime.of(20, 0);

    private RoomAvailability window(DayOfWeek day, LocalTime start, LocalTime end) {
        return new RoomAvailability(new Room(), day, start, end);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void covers_activityInsideWindow_returnsTrue() {
        RoomAvailability ra = window(DAY, START, END);

        // 08:00–09:30 is fully inside 08:00–20:00
        assertThat(ra.covers(DAY, LocalTime.of(8, 0), LocalTime.of(9, 30))).isTrue();
    }

    @Test
    void covers_exactBoundaryMatch_returnsTrue() {
        RoomAvailability ra = window(DAY, START, END);

        // activity exactly matches window
        assertThat(ra.covers(DAY, LocalTime.of(8, 0), LocalTime.of(20, 0))).isTrue();
    }

    // ------------------------------------------------------------------ out-of-bounds

    @Test
    void covers_activityEndsAfterWindow_returnsFalse() {
        RoomAvailability ra = window(DAY, START, END);

        // 19:30–21:10 extends past 20:00
        assertThat(ra.covers(DAY, LocalTime.of(19, 30), LocalTime.of(21, 10))).isFalse();
    }

    @Test
    void covers_activityStartsBeforeWindow_returnsFalse() {
        RoomAvailability ra = window(DAY, START, END);

        // 07:00–09:30 starts before 08:00
        assertThat(ra.covers(DAY, LocalTime.of(7, 0), LocalTime.of(9, 30))).isFalse();
    }

    // ------------------------------------------------------------------ wrong day

    @Test
    void covers_differentDay_returnsFalse() {
        RoomAvailability ra = window(DAY, START, END);   // MONDAY

        // Same time, but TUESDAY
        assertThat(ra.covers(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(9, 30))).isFalse();
    }
}
