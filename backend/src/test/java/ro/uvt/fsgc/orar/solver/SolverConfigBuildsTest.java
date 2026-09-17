package ro.uvt.fsgc.orar.solver;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;

/**
 * Guards the one mistake that is easy to make when adding a constraint: declaring it in the
 * provider but forgetting its {@code @ConstraintWeight} in the configuration. Timefold only
 * complains when the solver is built, which otherwise happens for the first time in production.
 */
class SolverConfigBuildsTest {

    @Test
    void everyConstraintHasAWeight() {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(TimetableSolution.class)
                .withEntityClasses(ScheduledActivity.class)
                .withConstraintProviderClass(TimetableConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(1L));
        // Building the factory is what fails loudly when a @ConstraintWeight is missing.
        assertNotNull(SolverFactory.create(config).buildSolver());
    }
}
