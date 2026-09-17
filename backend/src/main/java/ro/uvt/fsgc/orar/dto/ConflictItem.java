package ro.uvt.fsgc.orar.dto;

/**
 * One constraint under pressure, described for someone who has never seen the solver.
 *
 * @param constraint the internal key (kept stable — it is also the weight key)
 * @param label      the rule's name in Romanian
 * @param meaning    what the rule guarantees, in plain words
 * @param suggestion what the user can actually do about it
 */
public record ConflictItem(String constraint, String label, String severity, int matchCount,
                           String meaning, String suggestion) {
}
