package ro.uvt.fsgc.orar.dto;

/** One row of the compare table: a time budget and the quality it achieved. */
public record CompareResultDto(
        int budgetSeconds,
        long actualMillis,
        String score,
        long hardScore,
        long mediumScore,
        long softScore,
        int unassigned,
        int violatedHardConstraints) {
}
