package ro.uvt.fsgc.orar.domain;

/** What a room is suitable for. Excel `typology` column. */
public enum RoomTypology {
    AMPHITHEATER,
    COURSE,
    SEMINAR,
    LAB;

    /** Lenient parse of the Excel value (e.g. "Amphitheater", "Seminar", "Course"). */
    public static RoomTypology fromExcel(String raw) {
        if (raw == null) {
            return SEMINAR;
        }
        String v = raw.trim().toLowerCase();
        if (v.startsWith("amf") || v.startsWith("amph")) {
            return AMPHITHEATER;
        }
        if (v.startsWith("lab")) {
            return LAB;
        }
        if (v.startsWith("curs") || v.startsWith("course")) {
            return COURSE;
        }
        return SEMINAR;
    }
}