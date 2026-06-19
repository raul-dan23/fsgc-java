package ro.uvt.fsgc.orar.dto;

/**
 * One (program, year, specialization) a scheduled activity belongs to — a single column in the
 * FSGC-style weekly grid. {@code yearLabel} is the pre-rendered super-header ("LICENȚĂ/ANUL I").
 */
public record SectionView(
        String program,
        int year,
        String specialization,
        String yearLabel) {
}
