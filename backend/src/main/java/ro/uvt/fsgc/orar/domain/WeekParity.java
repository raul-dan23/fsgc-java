package ro.uvt.fsgc.orar.domain;

/**
 * When during the semester an activity is held.
 * SI (saptamani impare) -> ODD_WEEKS, SP (saptamani pare) -> EVEN_WEEKS.
 * Two activities with different parity may share the same room/slot without clashing.
 */
public enum WeekParity {
    EVERY_WEEK,
    EVEN_WEEKS,
    ODD_WEEKS
}