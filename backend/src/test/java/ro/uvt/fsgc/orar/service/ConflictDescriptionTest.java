package ro.uvt.fsgc.orar.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.solver.TimetableConstraintConfiguration;

/**
 * The conflict report is read by staff who have never heard of a solver, so every constraint that
 * can appear in it must have Romanian wording and none may leak the internal English key.
 *
 * <p>The list is read off the configuration by reflection rather than typed out here: a hand-kept
 * copy silently goes stale the moment a constraint is added or dropped, which is exactly what
 * happened to the previous version of this test.
 */
class ConflictDescriptionTest {

    private final SolverService service = new SolverService(null);

    /** Only hard and medium constraints reach the conflict table; soft ones shape quality. */
    private static List<String> reportedConstraints() {
        List<String> names = new ArrayList<>();
        TimetableConstraintConfiguration config = new TimetableConstraintConfiguration();
        for (Field f : TimetableConstraintConfiguration.class.getDeclaredFields()) {
            ConstraintWeight weight = f.getAnnotation(ConstraintWeight.class);
            if (weight == null) {
                continue;
            }
            f.setAccessible(true);
            try {
                HardMediumSoftScore score = (HardMediumSoftScore) f.get(config);
                if (score.hardScore() != 0 || score.mediumScore() != 0) {
                    names.add(weight.value());
                }
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        return names;
    }

    @Test
    void everyReportedConstraintHasRomanianWording() {
        List<String> reported = reportedConstraints();
        assertThat(reported).as("the configuration should declare hard constraints").isNotEmpty();
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
