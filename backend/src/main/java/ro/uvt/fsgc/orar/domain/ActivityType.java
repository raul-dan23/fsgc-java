package ro.uvt.fsgc.orar.domain;

/**
 * The kind of teaching activity. The Excel `activitate` column is messy and maps here:
 * any "Curs*" -> COURSE, any "Seminar*" -> SEMINAR, "DCT" (transversal discipline) -> SEMINAR.
 * Combined values like "Curs(SI)/Seminar(SP)" are split into two activities with different
 * {@link WeekParity}. Room-size variants ("Curs Mic", "Curs Amfiteatru") are NOT separate
 * types — required room size/typology is derived from the original raw value instead.
 */
public enum ActivityType {
    COURSE,
    SEMINAR,
    LAB
}