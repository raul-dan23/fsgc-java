package ro.uvt.fsgc.orar.solver;

import java.util.Comparator;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;

/**
 * Construction heuristic ordering: place the hardest activities first, when the most
 * (slot, room) pairs are still free. Harder = more students (fewer rooms fit), requires
 * amphitheater (very few rooms qualify), or serves more student groups simultaneously.
 */
public class ActivityDifficultyComparator implements Comparator<ScheduledActivity> {

    @Override
    public int compare(ScheduledActivity a, ScheduledActivity b) {
        return Comparator
                .comparingInt((ScheduledActivity x) -> x.isRequiresAmphitheater() ? 1 : 0)
                .thenComparingInt(ScheduledActivity::totalStudentCount)
                .thenComparingInt(x -> x.getStudentGroups().size())
                .reversed()
                .compare(a, b);
    }
}
