package ro.uvt.fsgc.orar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The conflict report is read by staff who have never heard of a solver, so every constraint
 * must have Romanian wording and none may leak the internal English key into the UI.
 */
class ConflictDescriptionTest {

    /** Every name declared in TimetableConstraintConfiguration. */
    private static final List<String> ALL = List.of(
            "No professor overlap", "No room overlap", "No student group overlap",
            "Room capacity sufficient", "Amphitheater required", "Room availability",
            "Master evening only", "Blocked day for terminal year", "Special category block",
            "Professor unavailability", "Professor forbidden room", "Professor only-this room",
            "Consecutive slots same building", "Unassigned activity",
            "Daily load balance per group", "Avoid gaps in a group's day",
            "Avoid late hours for license groups", "Compact a group's day",
            "Faculty-wide weekly balance", "Honor professor time preferences");

    private final SolverService service = new SolverService(null);

    @Test
    void everyHardConstraintHasRomanianWording() {
        // the hard/medium ones are the only ones that reach the conflict table
        List<String> reported = ALL.subList(0, 14);
        for (String name : reported) {
            String[] d = service.describe(name, 1);
            assertThat(d).as("description triple for " + name).hasSize(3);
            for (String part : d) {
                assertThat(part).as("no empty text for " + name).isNotBlank();
            }
            assertThat(d[0]).as(name + " must not fall through to the English key").isNotEqualTo(name);
        }
    }

    @Test
    void unknownConstraintStillProducesUsableText() {
        String[] d = service.describe("Some future rule", 3);
        assertThat(d[0]).isEqualTo("Some future rule");
        assertThat(d[1]).contains("nu a putut fi respectată");
        assertThat(d[2]).isNotBlank();
    }

    @Test
    void unassignedCountIsPluralisedInRomanian() {
        assertThat(service.describe("Unassigned activity", 1)[1]).contains("1 oră nu a încăput");
        assertThat(service.describe("Unassigned activity", 4)[1]).contains("4 ore nu au încăput");
    }
}
