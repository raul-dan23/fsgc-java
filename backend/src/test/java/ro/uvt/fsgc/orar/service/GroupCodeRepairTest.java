package ro.uvt.fsgc.orar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.StudentGroup;

/**
 * A cod_grupa that already belongs to another year of the same section is a typo, not a duplicate:
 * the Sectii sheet had "CRP I_gr.2" on a CRP year 2 row. Skipping it would leave that year one
 * group short and pile its seminars onto the group that remains, so the name is repaired instead.
 */
class GroupCodeRepairTest {

    private static StudentGroup group(String name, String spec, int year) {
        StudentGroup g = new StudentGroup();
        g.setName(name);
        g.setSpecialization(spec);
        g.setYear(year);
        return g;
    }

    @Test
    void takesTheHouseStyleFromASiblingOfTheSameYear() {
        assertEquals("CRP II_gr.2", ExcelImportService.repairedGroupName(
                "CRP I_gr.2", List.of(group("CRP II_gr.1", "CRP", 2)), "CRP", 2,
                new HashMap<>(), "CRP2"));
    }

    @Test
    void worksWithTheOtherNamingInTheWorkbook() {
        assertEquals("RISE1 - Grupa 3", ExcelImportService.repairedGroupName(
                "RISE2 - Grupa 3", List.of(group("RISE1 - Grupa 1", "RISE", 1)), "RISE", 1,
                new HashMap<>(), "RISE1"));
    }

    @Test
    void withoutASiblingItIsTheYearsFirstGroup() {
        // MMRPCD year 2 repeating year 1's code: the year has no other group, so it is named the
        // way an empty cod_grupa would have been named.
        Map<String, Integer> counts = new HashMap<>();
        assertEquals("MMRPCD2", ExcelImportService.repairedGroupName(
                "MMRPCD", List.of(), "MMRPCD", 2, counts, "MMRPCD2"));
    }

    @Test
    void aSiblingWithNoNumberCannotShowTheStyle() {
        assertNull(ExcelImportService.repairedGroupName(
                "AP1", List.of(group("AP", "AP", 1)), "AP", 1, new HashMap<>(), "AP1"));
    }
}
