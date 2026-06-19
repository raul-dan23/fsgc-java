package ro.uvt.fsgc.orar.domain;

/** Study cycle. The Excel `program` column holds "L" (licenta) or "M"/"master". */
public enum StudyProgram {
    LICENSE,
    MASTER;

    /** Lenient parse of the messy Excel value (e.g. "L ", "license", "master"). */
    public static StudyProgram fromExcel(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        if (v.startsWith("m")) {
            return MASTER;
        }
        if (v.startsWith("l")) {
            return LICENSE;
        }
        return null;
    }
}