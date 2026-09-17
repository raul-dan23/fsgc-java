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

    // ----- seminars are taught per group, courses are not -----

    @Test
    void seminarWithTwoGroups_becomesTwoSeparateActivities() {
        // "Politica externa a UE, s, gr. 1" and "... gr. 2" are two hours in the real timetable.
        List<Set<StudentGroup>> perGroup =
                ExcelImportService.splitPerGroup(ActivityType.SEMINAR, Set.of(G1, G2));
        assertEquals(2, perGroup.size());
        assertEquals(Set.of(G1, G2),
                perGroup.stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet()));
        perGroup.forEach(one -> assertEquals(1, one.size()));
    }

    @Test
    void seminarPerGroup_capacityIsOneGroup() {
        List<Set<StudentGroup>> perGroup =
                ExcelImportService.splitPerGroup(ActivityType.SEMINAR, Set.of(G1, G2));
        // 27 or 28 students each, so a 40-seat seminar room fits — no amphitheatre needed.
        perGroup.forEach(one -> {
            int students = one.stream().mapToInt(StudentGroup::getStudentCount).sum();
            assertTrue(students <= 40, "expected one group's worth of students, got " + students);
        });
    }

    @Test
    void courseWithTwoGroups_staysOneActivity() {
        // Only courses combine groups: g1+g2 sit in the lecture together.
        List<Set<StudentGroup>> perGroup =
                ExcelImportService.splitPerGroup(ActivityType.COURSE, Set.of(G1, G2));
        assertEquals(1, perGroup.size());
        assertEquals(Set.of(G1, G2), perGroup.get(0));
    }

    @Test
    void seminarWithOneGroup_staysOneActivity() {
        List<Set<StudentGroup>> perGroup =
                ExcelImportService.splitPerGroup(ActivityType.SEMINAR, Set.of(G1));
        assertEquals(1, perGroup.size());
    }

    @Test
    void seminarWithNoGroups_staysOneActivity() {
        // Faculty-wide rows (DCT, set_studenti = "-") have no group to split by.
        assertEquals(1, ExcelImportService.splitPerGroup(ActivityType.SEMINAR, Set.of()).size());
    }

    @Test
    void alternatingSeminar_isNotSplitAgain() {
        // The SI/SP halves already carry one group each, so they share one weekly hour
        // instead of becoming one hour per group.
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SI)/Seminar(SP)");
        List<GroupRef> refs = unmarked(G1, G2);
        Set<StudentGroup> oddHalf = ExcelImportService.audienceFor(specs.get(0), 0, specs, refs, true);
        assertEquals(1, ExcelImportService.splitPerGroup(ActivityType.SEMINAR, oddHalf).size());
    }

    // ----- a row that splits by group twice over -----

    @Test
    void subjectNamedAfterAGroup_withWholeYearAudience_isFlagged() {
        // "Administratie Publica Grupa 1" + set_studenti naming both groups becomes two seminars,
        // and the sibling row "... Grupa 2" becomes two more: four hours where two are needed.
        ro.uvt.fsgc.orar.dto.ImportResult result = new ro.uvt.fsgc.orar.dto.ImportResult();
        ExcelImportService.warnIfGroupNamedTwice("Administratie Publica Grupa 1",
                unmarked(G1, G2), result, 42);
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).message().contains("2 seminars"),
                result.getWarnings().get(0).message());
    }

    @Test
    void subjectNamedAfterAGroup_withOneGroup_isFine() {
        // The intended shape: the row names the group and the audience is just that group.
        ro.uvt.fsgc.orar.dto.ImportResult result = new ro.uvt.fsgc.orar.dto.ImportResult();
        ExcelImportService.warnIfGroupNamedTwice("Administratie Publica Grupa 1",
                unmarked(G1), result, 42);
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void subjectWithoutGroupInName_isFine() {
        // The other intended shape: one row for the subject, split per group by the importer.
        ro.uvt.fsgc.orar.dto.ImportResult result = new ro.uvt.fsgc.orar.dto.ImportResult();
        ExcelImportService.warnIfGroupNamedTwice("Administratie Publica",
                unmarked(G1, G2), result, 42);
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void groupNameMustActuallyMatchTheAudience() {
        // "... Grupa 3" with groups 1 and 2 is a different situation, so stay quiet.
        ro.uvt.fsgc.orar.dto.ImportResult result = new ro.uvt.fsgc.orar.dto.ImportResult();
        ExcelImportService.warnIfGroupNamedTwice("Ceva Grupa 3", unmarked(G1, G2), result, 42);
        assertTrue(result.getWarnings().isEmpty());
    }
}
