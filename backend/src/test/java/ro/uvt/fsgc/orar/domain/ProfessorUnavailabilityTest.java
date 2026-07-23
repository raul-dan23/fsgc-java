package ro.uvt.fsgc.orar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ProfessorUnavailabilityTest {

    // ------------------------------------------------------------------ helpers

    /** Whole-day unavailability (no time interval). */
    private ProfessorUnavailability wholeDay(DayOfWeek day) {
        ProfessorUnavailability u = new ProfessorUnavailability();
        u.setDayOfWeek(day);
        u.setStartTime(null);
        u.setEndTime(null);
        return u;
    }

    /** Time-interval unavailability on the given day. */
    private ProfessorUnavailability interval(DayOfWeek day, LocalTime start, LocalTime end) {
        ProfessorUnavailability u = new ProfessorUnavailability();
        u.setDayOfWeek(day);
        u.setStartTime(start);
        u.setEndTime(end);
        return u;
    }

    // ------------------------------------------------------------------ whole-day block

    @Test
    void blocks_wholeDayOnSameDay_returnsTrue() {
        ProfessorUnavailability u = wholeDay(DayOfWeek.MONDAY);

        assertThat(u.blocks(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 30))).isTrue();
    }

    @Test
    void blocks_wholeDayOnDifferentDay_returnsFalse() {
        ProfessorUnavailability u = wholeDay(DayOfWeek.MONDAY);

        assertThat(u.blocks(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(9, 30))).isFalse();
    }

    // ------------------------------------------------------------------ interval overlap

    @Test
    void blocks_intervalOverlap_returnsTrue() {
        // unavailability: MONDAY 09:00–11:00
        // query:          MONDAY 08:00–09:30  → overlaps (08:00 < 11:00 && 09:30 > 09:00)
        ProfessorUnavailability u = interval(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThat(u.blocks(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 30))).isTrue();
    }

    @Test
    void blocks_touchingEndBoundary_returnsFalse() {
        // unavailability: MONDAY 09:00–11:00
        // query:          MONDAY 11:00–12:30  → no overlap (11:00 is NOT before 11:00)
        ProfessorUnavailability u = interval(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThat(u.blocks(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 30))).isFalse();
    }

    @Test
    void blocks_activityBeforeInterval_returnsFalse() {
        // unavailability: MONDAY 09:00–11:00
        // query:          MONDAY 07:00–08:00  → ends before interval starts
        ProfessorUnavailability u = interval(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThat(u.blocks(DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(8, 0))).isFalse();
    }

    @Test
    void blocks_intervalOnDifferentDay_returnsFalse() {
        // Unavailability is MONDAY; querying WEDNESDAY
        ProfessorUnavailability u = interval(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThat(u.blocks(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))).isFalse();
    }
}
