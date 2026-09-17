package ro.uvt.fsgc.orar.dto;

import java.util.List;

/**
 * Suggested solver time budget for the current problem, with the numbers behind it so the user
 * can judge the advice instead of trusting a bare number.
 *
 * @param blockers structural impossibilities that no amount of solving time can fix
 */
public record BudgetAdvice(int quickSeconds, int recommendedSeconds, int thoroughSeconds,
                           String summary, List<String> reasons, List<String> blockers,
                           int activities, int rooms, int timeSlots, int occupancyPercent) {
}
