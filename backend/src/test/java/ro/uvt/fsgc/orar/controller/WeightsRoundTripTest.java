package ro.uvt.fsgc.orar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ConstraintWeights;
import ro.uvt.fsgc.orar.repository.ConstraintWeightsRepository;

/**
 * Every weight the UI shows must survive a save. The controller copies field by field, so a new
 * weight added to {@link ConstraintWeights} and to the page — but not to the copy — would look
 * adjustable and quietly keep its old value. That happened twice; this test is the tripwire.
 */
class WeightsRoundTripTest {

    @Test
    void everyWeightSurvivesAnUpdate() throws Exception {
        ConstraintWeightsRepository repo = mock(ConstraintWeightsRepository.class);
        when(repo.findById(ConstraintWeights.SINGLETON_ID))
                .thenReturn(Optional.of(new ConstraintWeights()));
        when(repo.save(any(ConstraintWeights.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // a distinct value per weight, so a field copied from the wrong source also shows up
        ConstraintWeights incoming = new ConstraintWeights();
        int value = 11;
        for (Field f : intWeightFields()) {
            f.setAccessible(true);
            f.setInt(incoming, value);
            value += 7;
        }

        ConstraintWeights saved = new WeightsController(repo).update(incoming).getBody();

        assertThat(saved).isNotNull();
        for (Field f : intWeightFields()) {
            f.setAccessible(true);
            assertThat(f.getInt(saved))
                    .as("weight '" + f.getName() + "' was not copied in WeightsController.update")
                    .isEqualTo(f.getInt(incoming));
        }
    }

    private static Field[] intWeightFields() {
        return java.util.Arrays.stream(ConstraintWeights.class.getDeclaredFields())
                .filter(f -> f.getType() == int.class)
                .toArray(Field[]::new);
    }
}
