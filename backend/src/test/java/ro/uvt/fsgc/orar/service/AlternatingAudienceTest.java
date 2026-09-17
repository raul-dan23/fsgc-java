package ro.uvt.fsgc.orar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.WeekParity;
import ro.uvt.fsgc.orar.service.ActivityTypeParser.ActivitySpec;
import ro.uvt.fsgc.orar.service.ExcelImportService.GroupRef;

/**
 * How the audience of a combined activitate value is divided between its halves. This is the
 * rule that decides whether one seminar hour per week means "both groups every week" or
 * "group 1 on odd weeks, group 2 on even weeks".
 */
class AlternatingAudienceTest {

    private static StudentGroup group(String name, int size) {
        StudentGroup g = new StudentGroup();
        g.setName(name);
        g.setStudentCount(size);
        return g;
    }

    private static ActivitySpec spec(ActivityType type, WeekParity parity) {
        return new ActivitySpec(type, parity, false, SpecialCategory.NORMAL);
    }

    private static final StudentGroup G1 = group("RISE1 - Grupa 1", 27);
    private static final StudentGroup G2 = group("RISE1 - Grupa 2", 28);

    private static List<GroupRef> unmarked(StudentGroup... groups) {
        return java.util.Arrays.stream(groups).map(g -> new GroupRef(g, null)).toList();
    }

    // ----- the case from the real workbook -----

    @Test
    void seminarSiSp_withTwoGroups_splitsOneGroupPerHalf() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SI)/Seminar(SP)");
        List<GroupRef> refs = unmarked(G1, G2);

        assertTrue(ExcelImportService.splitsAudienceByParity(specs, refs));
        // First group in the cell takes the first (odd) half, the second takes the even half.
        assertEquals(Set.of(G1), ExcelImportService.audienceFor(specs.get(0), 0, specs, refs, true));
        assertEquals(Set.of(G2), ExcelImportService.audienceFor(specs.get(1), 1, specs, refs, true));
    }

    @Test
    void seminarSiSp_capacityIsOneGroupNotBoth() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SI)/Seminar(SP)");
        List<GroupRef> refs = unmarked(G1, G2);
        // The defect this fixes: the room only has to hold 27, not 27+28.
        int oddHalfStudents = ExcelImportService.audienceFor(specs.get(0), 0, specs, refs, true)
                .stream().mapToInt(StudentGroup::getStudentCount).sum();
        assertEquals(27, oddHalfStudents);
    }

    // ----- cases that must NOT be split -----

    @Test
    void cursSiSeminarSp_keepsWholeAudienceOnBothHalves() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Curs(SI)/Seminar(SP)");
        List<GroupRef> refs = unmarked(G1, G2);
        // Same students attend the course on odd weeks and the seminar on even weeks.
        assertFalse(ExcelImportService.splitsAudienceByParity(specs, refs));
        assertEquals(Set.of(G1, G2),
                ExcelImportService.audienceFor(specs.get(0), 0, specs, refs, false));
        assertEquals(Set.of(G1, G2),
                ExcelImportService.audienceFor(specs.get(1), 1, specs, refs, false));
    }

    @Test
    void groupCountNotMatchingHalves_isNotSplit() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SI)/Seminar(SP)");
        // Three groups for two halves: the intended pairing is unknowable, so do not guess.
        List<GroupRef> refs = unmarked(G1, G2, group("RISE1 - Grupa 3", 26));
        assertFalse(ExcelImportService.splitsAudienceByParity(specs, refs));
    }

    @Test
    void plainSeminar_isNotSplit() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar");
        assertFalse(ExcelImportService.splitsAudienceByParity(specs, unmarked(G1, G2)));
    }

    @Test
    void noGroups_isNotSplit() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SI)/Seminar(SP)");
        // Faculty-wide rows (set_studenti = "-") have no audience to divide.
        assertFalse(ExcelImportService.splitsAudienceByParity(specs, List.of()));
    }

    // ----- explicit markers override the positional guess -----

    @Test
    void explicitMarkers_pinEachGroupToItsOwnHalf() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SI)/Seminar(SP)");
        // Reversed on purpose: Grupa 1 marked SP, Grupa 2 marked SI.
        List<GroupRef> refs = List.of(new GroupRef(G1, WeekParity.EVEN_WEEKS),
                new GroupRef(G2, WeekParity.ODD_WEEKS));

        assertTrue(ExcelImportService.splitsAudienceByParity(specs, refs));
        assertEquals(Set.of(G2), ExcelImportService.audienceFor(specs.get(0), 0, specs, refs, true));
        assertEquals(Set.of(G1), ExcelImportService.audienceFor(specs.get(1), 1, specs, refs, true));
    }

    @Test
    void explicitMarkers_workAcrossDifferentTypes() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Curs(SI)/Seminar(SP)");
        List<GroupRef> refs = List.of(new GroupRef(G1, WeekParity.ODD_WEEKS),
                new GroupRef(G2, WeekParity.EVEN_WEEKS));
        // An explicit marker is honored even where the positional guess would decline.
        assertTrue(ExcelImportService.splitsAudienceByParity(specs, refs));
        assertEquals(Set.of(G1), ExcelImportService.audienceFor(specs.get(0), 0, specs, refs, true));
        assertEquals(Set.of(G2), ExcelImportService.audienceFor(specs.get(1), 1, specs, refs, true));
    }

    @Test
    void unmarkedGroupAttendsEveryHalf_whenOthersAreMarked() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SI)/Seminar(SP)");
        StudentGroup shared = group("SP1", 30);
        List<GroupRef> refs = List.of(new GroupRef(G1, WeekParity.ODD_WEEKS),
                new GroupRef(G2, WeekParity.EVEN_WEEKS),
                new GroupRef(shared, null));

        assertTrue(ExcelImportService.splitsAudienceByParity(specs, refs));
        assertEquals(Set.of(G1, shared),
                ExcelImportService.audienceFor(specs.get(0), 0, specs, refs, true));
        assertEquals(Set.of(G2, shared),
                ExcelImportService.audienceFor(specs.get(1), 1, specs, refs, true));
    }
}
