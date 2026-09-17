package ro.uvt.fsgc.orar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.WeekParity;
import ro.uvt.fsgc.orar.service.ActivityTypeParser.ActivitySpec;
import ro.uvt.fsgc.orar.service.ActivityTypeParser.GroupToken;

/**
 * Covers the two Excel notations that decide how an alternating hour is split: the combined
 * {@code activitate} value and the optional per-group (SI)/(SP) marker in {@code set_studenti}.
 */
class ActivityTypeParserTest {

    // ----- combined activitate values -----

    @Test
    void seminarSiSp_isAlternatingSameType() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SI)/Seminar(SP)");
        assertEquals(2, specs.size());
        assertEquals(ActivityType.SEMINAR, specs.get(0).type());
        assertEquals(WeekParity.ODD_WEEKS, specs.get(0).parity());
        assertEquals(WeekParity.EVEN_WEEKS, specs.get(1).parity());
        // Same activity on alternating weeks -> the audience is split, one group per half.
        assertTrue(ActivityTypeParser.isAlternatingSameType(specs));
    }

    @Test
    void cursSiSeminarSp_isNotAlternatingSameType() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Curs(SI)/Seminar(SP)");
        assertEquals(2, specs.size());
        assertEquals(ActivityType.COURSE, specs.get(0).type());
        assertEquals(ActivityType.SEMINAR, specs.get(1).type());
        // Different types: the SAME audience alternates course/seminar, so it must not be split.
        assertFalse(ActivityTypeParser.isAlternatingSameType(specs));
    }

    @Test
    void plainSeminar_isNotAlternating() {
        assertFalse(ActivityTypeParser.isAlternatingSameType(ActivityTypeParser.parse("Seminar")));
    }

    @Test
    void sameTypeSameParity_isNotAlternating() {
        // Two halves that do not actually alternate cannot be paired off by parity.
        List<ActivitySpec> specs = List.of(
                new ActivitySpec(ActivityType.SEMINAR, WeekParity.ODD_WEEKS, false, null),
                new ActivitySpec(ActivityType.SEMINAR, WeekParity.ODD_WEEKS, false, null));
        assertFalse(ActivityTypeParser.isAlternatingSameType(specs));
    }

    @Test
    void everyWeekHalves_areNotAlternating() {
        List<ActivitySpec> specs = List.of(
                new ActivitySpec(ActivityType.SEMINAR, WeekParity.EVERY_WEEK, false, null),
                new ActivitySpec(ActivityType.SEMINAR, WeekParity.ODD_WEEKS, false, null));
        assertFalse(ActivityTypeParser.isAlternatingSameType(specs));
    }

    // ----- per-group parity markers -----

    @Test
    void groupToken_withoutMarker_hasNoParity() {
        GroupToken t = ActivityTypeParser.parseGroupToken("RISE1 - Grupa 1");
        assertEquals("RISE1 - Grupa 1", t.name());
        assertNull(t.parity());
    }

    @Test
    void groupToken_siMarkerStripped() {
        GroupToken t = ActivityTypeParser.parseGroupToken("RISE1 - Grupa 1(SI)");
        assertEquals("RISE1 - Grupa 1", t.name());
        assertEquals(WeekParity.ODD_WEEKS, t.parity());
    }

    @Test
    void groupToken_spMarkerToleratesSpacesAndCase() {
        GroupToken t = ActivityTypeParser.parseGroupToken("  RISE1 - Grupa 2 ( sp ) ");
        assertEquals("RISE1 - Grupa 2", t.name());
        assertEquals(WeekParity.EVEN_WEEKS, t.parity());
    }

    @Test
    void groupToken_unrelatedParenthesesKept() {
        // Only a trailing (SI)/(SP) is a marker; anything else stays part of the name.
        GroupToken t = ActivityTypeParser.parseGroupToken("SSEC1 (seria A)");
        assertEquals("SSEC1 (seria A)", t.name());
        assertNull(t.parity());
    }

    @Test
    void groupToken_blankIsEmpty() {
        assertEquals("", ActivityTypeParser.parseGroupToken("   ").name());
        assertEquals("", ActivityTypeParser.parseGroupToken(null).name());
    }
}
