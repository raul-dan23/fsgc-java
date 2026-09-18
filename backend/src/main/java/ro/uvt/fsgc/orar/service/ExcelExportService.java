package ro.uvt.fsgc.orar.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

/**
 * Exports the current timetable to .xlsx with a flat "Master" sheet plus three grid views
 * (Pe Grupa / Pe Sala / Pe Cadru Didactic): rows are the 40 fixed slots, columns are the
 * groups/rooms/professors, and each cell shows subject + type + the other dimensions.
 */
@Service
public class ExcelExportService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    /** FSGC sheet uses single-colon, no leading zero ("8:00-9:30"). */
    private static final DateTimeFormatter HM_SHORT = DateTimeFormatter.ofPattern("H:mm");
    private static final List<DayOfWeek> WEEK = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    private static final Map<DayOfWeek, String> DAY_RO = Map.of(
            DayOfWeek.MONDAY, "Luni", DayOfWeek.TUESDAY, "Marti", DayOfWeek.WEDNESDAY, "Miercuri",
            DayOfWeek.THURSDAY, "Joi", DayOfWeek.FRIDAY, "Vineri");
    private static final Map<DayOfWeek, String> DAY_RO_UPPER = Map.of(
            DayOfWeek.MONDAY, "LUNI", DayOfWeek.TUESDAY, "MARŢI", DayOfWeek.WEDNESDAY, "MIERCURI",
            DayOfWeek.THURSDAY, "JOI", DayOfWeek.FRIDAY, "VINERI");
    /** Roman numerals for "ANUL I".."ANUL VI". */
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI"};

    private final ScheduledActivityRepository activityRepo;
    private final TimeSlotRepository timeSlotRepo;

    public ExcelExportService(ScheduledActivityRepository activityRepo, TimeSlotRepository timeSlotRepo) {
        this.activityRepo = activityRepo;
        this.timeSlotRepo = timeSlotRepo;
    }

    @Transactional(readOnly = true)
    public byte[] export() {
        List<ScheduledActivity> all = activityRepo.findAll();
        List<ScheduledActivity> placed = all.stream().filter(ScheduledActivity::isPlaced).toList();
        List<TimeSlot> slots = timeSlotRepo.findAllByOrderByDayOfWeekAscSlotIndexAsc();

        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);
            CellStyle cell = wrapStyle(wb);
            CellStyle slotStyle = slotStyle(wb);

            // Primary sheet: the FSGC weekly grid (days as row-blocks, specialization-year columns).
            writeFsgcOrar(wb, placed, slots);

            writeMaster(wb, header, cell, all);
            writeGrid(wb, "Pe Grupa", placed, slots, header, cell, slotStyle,
                    a -> a.getStudentGroups().stream().map(g -> g.getName()).sorted().toList(),
                    a -> a.getSubject().getName() + " (" + typeWithParity(a) + ")\n"
                            + nullSafe(profName(a)) + "\n" + roomLabel(a));
            writeGrid(wb, "Pe Sala", placed, slots, header, cell, slotStyle,
                    a -> List.of(roomLabel(a)),
                    a -> a.getSubject().getName() + " (" + typeWithParity(a) + ")\n"
                            + String.join(", ", a.getStudentGroups().stream().map(g -> g.getName()).sorted().toList()));
            writeGrid(wb, "Pe Cadru Didactic", placed, slots, header, cell, slotStyle,
                    a -> a.getProfessor() == null ? List.of() : List.of(a.getProfessor().getName()),
                    a -> a.getSubject().getName() + " (" + typeWithParity(a) + ")\n"
                            + roomLabel(a) + "\n"
                            + String.join(", ", a.getStudentGroups().stream().map(g -> g.getName()).sorted().toList()));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A timetable column: one specialization within one (program, year). */
    private record Section(StudyProgram program, int year, String specialization)
            implements Comparable<Section> {
        @Override
        public int compareTo(Section o) {
            int c = Integer.compare(program.ordinal(), o.program.ordinal()); // LICENSE before MASTER
            if (c != 0) return c;
            c = Integer.compare(year, o.year);
            if (c != 0) return c;
            return specialization.compareToIgnoreCase(o.specialization);
        }

        String yearLabel() {
            String prog = program == StudyProgram.MASTER ? "MASTER" : "LICENȚĂ";
            String r = year >= 0 && year < ROMAN.length ? ROMAN[year] : Integer.toString(year);
            return prog + "/ANUL " + r;
        }
    }

    /**
     * Writes the FSGC-style weekly grid (matches "Orar FSGC ....xlsx", sheet DSG):
     * <ul>
     *   <li>columns = each specialization, grouped under a merged (program, year) super-header;</li>
     *   <li>rows = each weekday is a block of 8 modules, every module being two rows
     *       (subject+professor on top, room below);</li>
     *   <li>column A = day (merged over its block), column B = interval / "Sala" labels;</li>
     *   <li>a common course spanning several specializations becomes one merged cell.</li>
     * </ul>
     */
    private void writeFsgcOrar(Workbook wb, List<ScheduledActivity> placed, List<TimeSlot> slots) {
        Sheet s = wb.createSheet("Orar");
        CellStyle yearStyle = bordered(wb, true, true, IndexedColors.GREY_25_PERCENT);
        CellStyle specStyle = bordered(wb, true, true, IndexedColors.GREY_25_PERCENT);
        CellStyle dayStyle = bordered(wb, true, true, IndexedColors.GREY_25_PERCENT);
        CellStyle labelStyle = bordered(wb, true, true, null);
        CellStyle actStyle = bordered(wb, false, true, null);
        CellStyle roomStyle = bordered(wb, false, true, null);

        // --- column model: distinct sections, sorted (license years first, then specialization) ---
        TreeSet<Section> sectionSet = new TreeSet<>();
        for (ScheduledActivity a : placed) {
            for (StudentGroup g : a.getStudentGroups()) {
                sectionSet.add(new Section(g.getStudyProgram(), g.getYear(), g.getSpecialization()));
            }
        }
        List<Section> sections = new ArrayList<>(sectionSet);
        Map<Section, Integer> colOf = new LinkedHashMap<>();
        int firstDataCol = 2; // col 0 = day, col 1 = interval/Sala
        for (int i = 0; i < sections.size(); i++) {
            colOf.put(sections.get(i), firstDataCol + i);
        }

        // --- header row 0: (program, year) super-header merged across its specializations ---
        Row h0 = s.createRow(0);
        Row h1 = s.createRow(1);
        addMerge(s, 0, 1, 0, 1); // empty top-left corner over the day + label columns
        set(h0, 0, "", yearStyle);
        set(h1, 0, "", yearStyle);
        set(h1, 1, "", yearStyle);
        int run = 0;
        while (run < sections.size()) {
            Section start = sections.get(run);
            int end = run;
            while (end + 1 < sections.size()
                    && sections.get(end + 1).program() == start.program()
                    && sections.get(end + 1).year() == start.year()) {
                end++;
            }
            int c0 = firstDataCol + run;
            int c1 = firstDataCol + end;
            set(h0, c0, start.yearLabel(), yearStyle);
            if (c1 > c0) {
                addMerge(s, 0, 0, c0, c1);
            }
            for (int i = run; i <= end; i++) {
                set(h1, firstDataCol + i, sections.get(i).specialization(), specStyle);
            }
            run = end + 1;
        }
        // fill blank year-header cells so borders render across the whole row
        for (int c = firstDataCol; c < firstDataCol + sections.size(); c++) {
            if (h0.getCell(c) == null) set(h0, c, "", yearStyle);
        }

        // --- body: per weekday, 8 modules x 2 rows ---
        // slots grouped by day, ordered by slotIndex
        Map<DayOfWeek, List<TimeSlot>> byDay = new LinkedHashMap<>();
        for (TimeSlot ts : slots) {
            byDay.computeIfAbsent(ts.getDayOfWeek(), k -> new ArrayList<>()).add(ts);
        }
        // map slotId -> the activity row index (room row is +1)
        Map<Long, Integer> actRowOf = new LinkedHashMap<>();
        int r = 2;
        for (DayOfWeek day : WEEK) {
            List<TimeSlot> daySlots = byDay.getOrDefault(day, List.of());
            if (daySlots.isEmpty()) {
                continue;
            }
            daySlots.sort(Comparator.comparingInt(TimeSlot::getSlotIndex));
            int blockStart = r;
            for (TimeSlot ts : daySlots) {
                Row actRow = s.createRow(r);
                Row roomRow = s.createRow(r + 1);
                set(actRow, 1, ts.getStartTime().format(HM_SHORT) + "-" + ts.getEndTime().format(HM_SHORT), labelStyle);
                set(roomRow, 1, "Sala", labelStyle);
                // pre-fill data cells with empty bordered cells so the grid reads as a table
                for (int c = firstDataCol; c < firstDataCol + sections.size(); c++) {
                    set(actRow, c, "", actStyle);
                    set(roomRow, c, "", roomStyle);
                }
                actRowOf.put(ts.getId(), r);
                r += 2;
            }
            // day name spans the whole block in column A
            set(s.getRow(blockStart), 0, DAY_RO_UPPER.get(day), dayStyle);
            for (int rr = blockStart + 1; rr < r; rr++) {
                set(s.getRow(rr), 0, "", dayStyle);
            }
            if (r - 1 > blockStart) {
                addMerge(s, blockStart, r - 1, 0, 0);
            }
        }

        // --- place activities ---
        // remember cells already covered by a merge, so a parity-split / shared slot does not
        // trigger an overlapping-merge exception from POI.
        java.util.Set<String> merged = new java.util.HashSet<>();
        for (ScheduledActivity a : placed) {
            Integer actR = actRowOf.get(a.getTimeSlot().getId());
            if (actR == null) continue;
            Row actRow = s.getRow(actR);
            Row roomRow = s.getRow(actR + 1);
            String room = roomLabel(a);

            // one column per section the activity is taught to, in the order of the header
            java.util.Map<Integer, Section> sectionOfCol = new java.util.LinkedHashMap<>();
            a.getStudentGroups().stream()
                    .map(g -> new Section(g.getStudyProgram(), g.getYear(), g.getSpecialization()))
                    .distinct()
                    .forEach(sec -> {
                        Integer col = colOf.get(sec);
                        if (col != null) {
                            sectionOfCol.putIfAbsent(col, sec);
                        }
                    });
            List<Integer> cols = sectionOfCol.keySet().stream().sorted().toList();

            for (int col : cols) {
                appendCell(actRow.getCell(col), fsgcCellText(a, sectionOfCol.get(col)));
                appendCell(roomRow.getCell(col), room);
            }
            // merge contiguous runs of columns (a common course shown as one wide cell)
            for (int i = 0; i < cols.size(); ) {
                int j = i;
                while (j + 1 < cols.size() && cols.get(j + 1) == cols.get(j) + 1) j++;
                if (j > i && freeForMerge(merged, actR, cols.get(i), cols.get(j))) {
                    addMerge(s, actR, actR, cols.get(i), cols.get(j));
                    addMerge(s, actR + 1, actR + 1, cols.get(i), cols.get(j));
                    for (int c = cols.get(i); c <= cols.get(j); c++) {
                        merged.add(actR + ":" + c);
                    }
                }
                i = j + 1;
            }
        }

        s.setColumnWidth(0, 11 * 256);
        s.setColumnWidth(1, 12 * 256);
        for (int i = 0; i < sections.size(); i++) {
            s.setColumnWidth(firstDataCol + i, 26 * 256);
        }
        s.createFreezePane(2, 2);
    }

    /** "Materie, Profesor, tip" — matches the FSGC convention (tip: c=curs, s=seminar, l=laborator). */
    /**
     * What one cell of the weekly grid reads: subject, who teaches it, what kind of hour it is,
     * which group of that section it is for, and — for an alternating hour — which week. The group
     * is narrowed to the column's own section: a common course spans several columns, and the
     * group numbers of a different section would mean nothing under that heading.
     */
    private static String fsgcCellText(ScheduledActivity a, Section section) {
        StringBuilder sb = new StringBuilder(a.getSubject().getName());
        if (a.getProfessor() != null) {
            sb.append(", ").append(a.getProfessor().getName());
        }
        sb.append(", ").append(typeAbbrev(a.getActivityType()));
        String groups = groupLabel(a, section);
        if (!groups.isEmpty()) {
            sb.append(", ").append(groups);
        }
        String parity = parityLabel(a);
        if (!parity.isEmpty()) {
            sb.append(", ").append(parity);
        }
        return sb.toString();
    }

    /** "gr. 1" / "gr. 1, 2"; empty when the hour is for the whole year of that section. */
    private static String groupLabel(ScheduledActivity a, Section section) {
        List<String> numbers = a.getStudentGroups().stream()
                .filter(g -> section == null || new Section(g.getStudyProgram(), g.getYear(),
                        g.getSpecialization()).equals(section))
                .map(g -> groupNumber(g.getName()))
                .filter(java.util.Objects::nonNull)
                .distinct().sorted().toList();
        return numbers.isEmpty() ? "" : "gr. " + String.join(", ", numbers);
    }

    /** "MD II_gr.2" -> "2"; null for a group that is a whole year ("J II"). */
    private static String groupNumber(String name) {
        if (name == null) {
            return null;
        }
        var m = java.util.regex.Pattern
                // no \b before "gr": the workbook writes "J I_gr.1", and "_" is a word character
                .compile("gr(?:upa)?\\.?\\s*(\\d+)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(name.trim());
        return m.find() ? m.group(1) : null;
    }

    /** "SEMINAR" or "SEMINAR, SP" — the week matters wherever the hour is listed. */
    private static String typeWithParity(ScheduledActivity a) {
        String parity = parityLabel(a);
        return parity.isEmpty() ? a.getActivityType().name() : a.getActivityType() + ", " + parity;
    }

    /** SI for odd weeks, SP for even, empty for an hour held every week. */
    private static String parityLabel(ScheduledActivity a) {
        return switch (a.getWeekParity()) {
            case ODD_WEEKS -> "SI";
            case EVEN_WEEKS -> "SP";
            default -> "";
        };
    }

    private static String typeAbbrev(ActivityType t) {
        return switch (t) {
            case COURSE -> "c";
            case SEMINAR -> "s";
            case LAB -> "l";
        };
    }

    /** Writes text into a pre-created cell, stacking with a separator if it already has content. */
    private static void appendCell(org.apache.poi.ss.usermodel.Cell c, String text) {
        if (c == null) return;
        String existing = c.getStringCellValue();
        c.setCellValue(existing == null || existing.isEmpty() ? text : existing + "\n----\n" + text);
    }

    private static boolean freeForMerge(java.util.Set<String> merged, int actRow, int c0, int c1) {
        for (int c = c0; c <= c1; c++) {
            if (merged.contains(actRow + ":" + c)) {
                return false;
            }
        }
        return true;
    }

    private static void addMerge(Sheet s, int r0, int r1, int c0, int c1) {
        s.addMergedRegion(new CellRangeAddress(r0, r1, c0, c1));
    }

    private CellStyle bordered(Workbook wb, boolean bold, boolean center, IndexedColors fill) {
        CellStyle st = wb.createCellStyle();
        st.setWrapText(true);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        if (center) st.setAlignment(HorizontalAlignment.CENTER);
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
        if (bold) {
            Font f = wb.createFont();
            f.setBold(true);
            st.setFont(f);
        }
        if (fill != null) {
            st.setFillForegroundColor(fill.getIndex());
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return st;
    }

    private void writeMaster(Workbook wb, CellStyle header, CellStyle cell, List<ScheduledActivity> all) {
        Sheet s = wb.createSheet("Master");
        String[] cols = {"Zi", "Modul", "Interval", "Disciplina", "Cod", "Tip", "Cadru Didactic",
                "Sala", "Grupe", "Studenti", "Paritate"};
        Row h = s.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            var c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(header);
        }
        List<ScheduledActivity> sorted = new ArrayList<>(all);
        sorted.sort(Comparator
                .comparing((ScheduledActivity a) -> a.getTimeSlot() == null ? 99 : a.getTimeSlot().getDayOfWeek().getValue())
                .thenComparing(a -> a.getTimeSlot() == null ? 99 : a.getTimeSlot().getSlotIndex()));
        int r = 1;
        for (ScheduledActivity a : sorted) {
            Row row = s.createRow(r++);
            TimeSlot ts = a.getTimeSlot();
            set(row, 0, ts == null ? "(neplasat)" : DAY_RO.get(ts.getDayOfWeek()), cell);
            set(row, 1, ts == null ? "" : "M" + ts.getSlotIndex(), cell);
            set(row, 2, ts == null ? "" : ts.getStartTime().format(HM) + "-" + ts.getEndTime().format(HM), cell);
            set(row, 3, a.getSubject().getName(), cell);
            set(row, 4, a.getSubject().getCode(), cell);
            set(row, 5, a.getActivityType().name(), cell);
            set(row, 6, profName(a), cell);
            set(row, 7, roomLabel(a), cell);
            set(row, 8, String.join(", ", a.getStudentGroups().stream().map(g -> g.getName()).sorted().toList()), cell);
            set(row, 9, Integer.toString(a.totalStudentCount()), cell);
            set(row, 10, a.getWeekParity().name(), cell);
        }
        for (int i = 0; i < cols.length; i++) {
            s.autoSizeColumn(i);
        }
    }

    /**
     * Writes a grid view: 40 slot rows x N columns (keyed by groups/rooms/professors). The
     * {@code columnKeys} function yields which columns an activity occupies; {@code cellText}
     * renders its cell content.
     */
    private void writeGrid(Workbook wb, String name, List<ScheduledActivity> placed, List<TimeSlot> slots,
                           CellStyle header, CellStyle cell, CellStyle slotStyle,
                           Function<ScheduledActivity, List<String>> columnKeys,
                           Function<ScheduledActivity, String> cellText) {
        Sheet s = wb.createSheet(name);

        TreeSet<String> columnSet = new TreeSet<>();
        for (ScheduledActivity a : placed) {
            columnSet.addAll(columnKeys.apply(a));
        }
        List<String> columns = new ArrayList<>(columnSet);
        Map<String, Integer> colIndex = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            colIndex.put(columns.get(i), i + 1);
        }

        Row h = s.createRow(0);
        set(h, 0, "Zi / Modul", header);
        for (int i = 0; i < columns.size(); i++) {
            set(h, i + 1, columns.get(i), header);
        }

        // one row per slot; remember the row index for each slot id
        Map<Long, Integer> slotRow = new LinkedHashMap<>();
        int r = 1;
        for (TimeSlot ts : slots) {
            Row row = s.createRow(r);
            set(row, 0, DAY_RO.get(ts.getDayOfWeek()) + " M" + ts.getSlotIndex()
                    + " (" + ts.getStartTime().format(HM) + ")", slotStyle);
            slotRow.put(ts.getId(), r);
            r++;
        }

        for (ScheduledActivity a : placed) {
            Integer rowIdx = slotRow.get(a.getTimeSlot().getId());
            if (rowIdx == null) {
                continue;
            }
            Row row = s.getRow(rowIdx);
            String text = cellText.apply(a);
            for (String key : columnKeys.apply(a)) {
                Integer ci = colIndex.get(key);
                if (ci == null) {
                    continue;
                }
                var c = row.getCell(ci);
                if (c == null) {
                    c = row.createCell(ci);
                    c.setCellValue(text);
                } else {
                    // two activities in the same cell (e.g. parity-split) -> stack them
                    c.setCellValue(c.getStringCellValue() + "\n----\n" + text);
                }
                c.setCellStyle(cell);
            }
        }
        s.setColumnWidth(0, 22 * 256);
        for (int i = 1; i <= columns.size(); i++) {
            s.setColumnWidth(i, 30 * 256);
        }
    }

    private static String profName(ScheduledActivity a) {
        return a.getProfessor() == null ? "" : a.getProfessor().getName();
    }

    /** What goes in the room cell: the room, or ONLINE for an activity that has none by design. */
    private static String roomLabel(ScheduledActivity a) {
        if (a.getRoom() != null) {
            return a.getRoom().getName();
        }
        return a.isOnline() ? "ONLINE" : "";
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static void set(Row row, int col, String value, CellStyle style) {
        var c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle wrapStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setWrapText(true);
        s.setVerticalAlignment(VerticalAlignment.TOP);
        return s;
    }

    private CellStyle slotStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }
}
