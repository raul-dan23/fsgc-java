package ro.uvt.fsgc.orar.service;

import java.time.LocalTime;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

/** Lenient cell readers that tolerate the messy source data (strings where numbers/times are expected). */
final class ExcelCells {

    private ExcelCells() {
    }

    /** Trimmed string value of a cell, or null if blank. Numbers are rendered without trailing ".0". */
    static String str(Row row, int col) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        String s = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield Long.toString((long) d);
                }
                yield Double.toString(d);
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    yield Double.toString(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /** Parses an integer from a possibly-string cell; returns {@code def} if absent/unparseable. */
    static int intVal(Row row, int col, int def) {
        String s = str(row, col);
        if (s == null) {
            return def;
        }
        try {
            // tolerate "40", "40.0", "  40 "
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Parses a time from a "HH:mm" string or a numeric/time cell; null if absent/unparseable. */
    static LocalTime time(Row row, int col) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalTime();
            }
            // fraction of a day
            double frac = cell.getNumericCellValue();
            int minutes = (int) Math.round(frac * 24 * 60);
            return LocalTime.of((minutes / 60) % 24, minutes % 60);
        }
        String s = str(row, col);
        if (s == null) {
            return null;
        }
        s = s.replace('.', ':');
        String[] parts = s.split(":");
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            return LocalTime.of(h, m);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** True if the entire row is blank/empty. */
    static boolean isBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            if (str(row, c) != null) {
                return false;
            }
        }
        return true;
    }
}
