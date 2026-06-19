package ro.uvt.fsgc.orar.dto;

/** One violated/blocked constraint with a human-readable suggestion. */
public record ConflictItem(String constraint, String severity, int matchCount, String suggestion) {
}
