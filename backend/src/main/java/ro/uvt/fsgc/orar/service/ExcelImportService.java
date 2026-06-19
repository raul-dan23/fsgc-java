package ro.uvt.fsgc.orar.service;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomAvailability;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.dto.ImportResult;
import ro.uvt.fsgc.orar.repository.BlockedDayRuleRepository;
import ro.uvt.fsgc.orar.repository.BuildingRepository;
import ro.uvt.fsgc.orar.repository.ProfessorRepository;
import ro.uvt.fsgc.orar.repository.ProfessorRoomRestrictionRepository;
import ro.uvt.fsgc.orar.repository.ProfessorUnavailabilityRepository;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.SpecialBlockRuleRepository;
import ro.uvt.fsgc.orar.repository.StudentGroupRepository;
import ro.uvt.fsgc.orar.repository.SubjectRepository;

/**
 * Parses the 4-sheet import workbook (Sectii, Profesori, Sali, Discipline) with Apache POI,
 * validates references with 1-based Excel row numbers, and saves everything transactionally.
 * Any validation error rolls back the whole import (all-or-nothing).
 */
@Service
public class ExcelImportService {

    private static final String SHEET_SECTIONS = "Sectii";
    private static final String SHEET_PROFESSORS = "Profesori";
    private static final String SHEET_ROOMS = "Sali";
    private static final String SHEET_SUBJECTS = "Discipline";

    private final StudentGroupRepository groupRepo;
    private final ProfessorRepository professorRepo;
    private final RoomRepository roomRepo;
    private final SubjectRepository subjectRepo;
    private final ScheduledActivityRepository activityRepo;
    private final BuildingRepository buildingRepo;
    private final SpecialBlockRuleRepository specialBlockRepo;
    private final ProfessorUnavailabilityRepository profUnavailRepo;
    private final ProfessorRoomRestrictionRepository profRoomRepo;
    private final BlockedDayRuleRepository blockedDayRepo;

    public ExcelImportService(StudentGroupRepository groupRepo, ProfessorRepository professorRepo,
                              RoomRepository roomRepo, SubjectRepository subjectRepo,
                              ScheduledActivityRepository activityRepo, BuildingRepository buildingRepo,
                              SpecialBlockRuleRepository specialBlockRepo,
                              ProfessorUnavailabilityRepository profUnavailRepo,
                              ProfessorRoomRestrictionRepository profRoomRepo,
                              BlockedDayRuleRepository blockedDayRepo) {
        this.groupRepo = groupRepo;
        this.professorRepo = professorRepo;
        this.roomRepo = roomRepo;
        this.subjectRepo = subjectRepo;
        this.activityRepo = activityRepo;
        this.buildingRepo = buildingRepo;
        this.specialBlockRepo = specialBlockRepo;
        this.profUnavailRepo = profUnavailRepo;
        this.profRoomRepo = profRoomRepo;
        this.blockedDayRepo = blockedDayRepo;
    }

    /** Thrown to trigger transaction rollback while carrying the partial result with errors. */
    public static class ImportFailedException extends RuntimeException {
        private final transient ImportResult result;

        ImportFailedException(ImportResult result) {
            super("Import failed with " + result.getErrors().size() + " error(s)");
            this.result = result;
        }

        public ImportResult getResult() {
            return result;
        }
    }

    @Transactional
    public ImportResult importWorkbook(InputStream in) {
        ImportResult result = new ImportResult();
        try (Workbook wb = new XSSFWorkbook(in)) {
            if (missingSheets(wb, result)) {
                throw new ImportFailedException(result);
            }

            wipeExistingData();

            Map<String, StudentGroup> groupByName = new HashMap<>();
            Map<String, List<StudentGroup>> groupBySpecYear = new HashMap<>();
            parseSections(wb.getSheet(SHEET_SECTIONS), result, groupByName, groupBySpecYear);

            Map<String, Professor> professorByName = new HashMap<>();
            parseProfessors(wb.getSheet(SHEET_PROFESSORS), result, professorByName);

            parseRooms(wb.getSheet(SHEET_ROOMS), result);

            parseSubjects(wb.getSheet(SHEET_SUBJECTS), result, professorByName, groupByName, groupBySpecYear);

            if (!result.getErrors().isEmpty()) {
                throw new ImportFailedException(result);
            }
            result.setSuccess(true);
            return result;
        } catch (ImportFailedException e) {
            throw e;
        } catch (Exception e) {
            result.addError("(workbook)", 0, "Could not read file: " + e.getMessage());
            throw new ImportFailedException(result);
        }
    }

    private boolean missingSheets(Workbook wb, ImportResult result) {
        boolean missing = false;
        for (String s : List.of(SHEET_SECTIONS, SHEET_PROFESSORS, SHEET_ROOMS, SHEET_SUBJECTS)) {
            if (wb.getSheet(s) == null) {
                result.addError("(workbook)", 0, "Missing required sheet: " + s);
                missing = true;
            }
        }
        return missing;
    }

    /** Order matters for FK integrity. Re-import resets the whole dataset including derived rules. */
    private void wipeExistingData() {
        activityRepo.deleteAllInBatch();
        specialBlockRepo.deleteAllInBatch();
        profUnavailRepo.deleteAllInBatch();
        profRoomRepo.deleteAllInBatch();
        blockedDayRepo.deleteAllInBatch();
        // Bulk delete issues immediate SQL (before the new inserts flush); the DB-level
        // ON DELETE CASCADE on room_availability/room_equipment removes the children.
        roomRepo.deleteAllInBatch();
        subjectRepo.deleteAllInBatch();
        groupRepo.deleteAllInBatch();
        professorRepo.deleteAllInBatch();
        buildingRepo.deleteAllInBatch();
    }

    // ------------------------------------------------------------------ Sectii

    private void parseSections(Sheet sheet, ImportResult result,
                               Map<String, StudentGroup> byName,
                               Map<String, List<StudentGroup>> bySpecYear) {
        Map<String, Integer> specYearCount = new HashMap<>();
        int saved = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (ExcelCells.isBlank(row)) {
                continue;
            }
            int excelRow = r + 1;
            String spec = ExcelCells.str(row, 0);
            if (spec == null) {
                result.addWarning(SHEET_SECTIONS, excelRow, "Missing sectie, row skipped");
                continue;
            }
            int year = ExcelCells.intVal(row, 1, -1);
            StudyProgram program = StudyProgram.fromExcel(ExcelCells.str(row, 2));
            if (program == null) {
                program = StudyProgram.LICENSE;
                result.addWarning(SHEET_SECTIONS, excelRow, "Unknown program, defaulted to LICENSE");
            }
            int nrAn = ExcelCells.intVal(row, 3, 0);
            String codGrupa = ExcelCells.str(row, 4);
            int nrGrupa = ExcelCells.intVal(row, 5, nrAn);
            String dept = ExcelCells.str(row, 6);

            String specYearKey = (spec + year).replaceAll("\\s+", "").toUpperCase();
            String name;
            int count;
            if (codGrupa != null) {
                name = codGrupa;
                count = nrGrupa;
            } else {
                int n = specYearCount.merge(specYearKey, 1, Integer::sum);
                name = (n == 1) ? (spec + year) : (spec + year + " - Grupa " + n);
                count = nrAn;
            }
            if (byName.containsKey(name)) {
                result.addWarning(SHEET_SECTIONS, excelRow, "Duplicate group name '" + name + "', row skipped");
                continue;
            }
            StudentGroup g = new StudentGroup();
            g.setName(name);
            g.setSpecialization(spec);
            g.setYear(year);
            g.setStudyProgram(program);
            g.setStudentCount(count);
            g.setDepartment(dept);
            groupRepo.save(g);
            byName.put(name, g);
            bySpecYear.computeIfAbsent(specYearKey, k -> new ArrayList<>()).add(g);
            saved++;
        }
        result.setStudentGroups(saved);
    }

    // ------------------------------------------------------------------ Profesori

    private void parseProfessors(Sheet sheet, ImportResult result, Map<String, Professor> byName) {
        int saved = 0;
        Set<String> depts = new HashSet<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (ExcelCells.isBlank(row)) {
                continue;
            }
            int excelRow = r + 1;
            String name = ExcelCells.str(row, 0);
            if (name == null) {
                result.addWarning(SHEET_PROFESSORS, excelRow, "Missing professor name, row skipped");
                continue;
            }
            String key = normalizeName(name);
            if (byName.containsKey(key)) {
                continue; // duplicate professor row, ignore silently
            }
            Professor p = new Professor();
            p.setName(name);
            p.setEmail(ExcelCells.str(row, 1));
            p.setTitle(ExcelCells.str(row, 2));
            String dept = ExcelCells.str(row, 3);
            p.setDepartment(dept);
            if (dept != null) {
                depts.add(dept);
            }
            professorRepo.save(p);
            byName.put(key, p);
            saved++;
        }
        result.setProfessors(saved);
        result.setDepartments(depts.size());
    }

    // ------------------------------------------------------------------ Sali

    private void parseRooms(Sheet sheet, ImportResult result) {
        int saved = 0;
        int avail = 0;
        Set<String> seen = new HashSet<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (ExcelCells.isBlank(row)) {
                continue;
            }
            int excelRow = r + 1;
            String name = ExcelCells.str(row, 0);
            if (name == null) {
                result.addWarning(SHEET_ROOMS, excelRow, "Missing room name, row skipped");
                continue;
            }
            if (!seen.add(name)) {
                result.addWarning(SHEET_ROOMS, excelRow, "Duplicate room '" + name + "', row skipped");
                continue;
            }
            Room room = new Room();
            room.setName(name);
            room.setDepartment(ExcelCells.str(row, 1));
            room.setFloor(ExcelCells.str(row, 2));
            int capacity = ExcelCells.intVal(row, 3, 0);
            if (capacity <= 0) {
                result.addError(SHEET_ROOMS, excelRow, "Room '" + name + "' has invalid capacity");
            }
            room.setCapacity(capacity);
            room.setTypology(RoomTypology.fromExcel(ExcelCells.str(row, 4)));
            room.setUsageRestrictions(ExcelCells.str(row, 8));

            List<DayOfWeek> days = parseDays(ExcelCells.str(row, 5));
            LocalTime from = ExcelCells.time(row, 6);
            LocalTime until = ExcelCells.time(row, 7);
            if (from == null) {
                from = LocalTime.of(8, 0);
            }
            if (until == null) {
                until = LocalTime.of(21, 10);
            }
            for (DayOfWeek d : days) {
                room.getAvailabilities().add(new RoomAvailability(room, d, from, until));
                avail++;
            }
            roomRepo.save(room);
            saved++;
        }
        result.setRooms(saved);
        result.setRoomAvailabilities(avail);
    }

    // ------------------------------------------------------------------ Discipline

    private void parseSubjects(Sheet sheet, ImportResult result, Map<String, Professor> professorByName,
                               Map<String, StudentGroup> groupByName,
                               Map<String, List<StudentGroup>> groupBySpecYear) {
        Map<String, Subject> subjectByCode = new HashMap<>();
        int subjects = 0;
        int activities = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (ExcelCells.isBlank(row)) {
                continue;
            }
            int excelRow = r + 1;
            String setStudenti = ExcelCells.str(row, 0);
            String materie = ExcelCells.str(row, 1);
            String codMaterie = ExcelCells.str(row, 2);
            String profName = ExcelCells.str(row, 3);
            String activitate = ExcelCells.str(row, 4);
            String dept = ExcelCells.str(row, 5);

            if (materie == null || codMaterie == null) {
                result.addWarning(SHEET_SUBJECTS, excelRow, "Missing materie/cod_materie, row skipped");
                continue;
            }

            List<ActivityTypeParser.ActivitySpec> specs = ActivityTypeParser.parse(activitate);
            if (specs.isEmpty()) {
                result.addWarning(SHEET_SUBJECTS, excelRow,
                        "Unrecognized activitate '" + activitate + "', row skipped");
                continue;
            }

            // Resolve professor (DCT/transversal rows may have a professor too). If the name is not
            // in the Profesori sheet (common when that sheet is incomplete), auto-create a placeholder
            // professor and warn, instead of failing the whole import.
            Professor professor = null;
            if (profName != null) {
                professor = professorByName.get(normalizeName(profName));
                if (professor == null) {
                    professor = createPlaceholderProfessor(profName, professorByName);
                    result.addWarning(SHEET_SUBJECTS, excelRow,
                            "Professor '" + profName + "' is not in Profesori sheet — auto-created. "
                                    + "Add it there for title/email/restrictions.");
                }
            }

            // Resolve student groups from set_studenti.
            Set<StudentGroup> groups = resolveGroups(setStudenti, groupByName, groupBySpecYear, result, excelRow);

            Subject subject = subjectByCode.get(codMaterie);
            if (subject == null) {
                subject = new Subject();
                subject.setCode(codMaterie);
                subject.setName(materie);
                subject.setDepartment(dept);
                subjectRepo.save(subject);
                subjectByCode.put(codMaterie, subject);
                subjects++;
            }

            for (ActivityTypeParser.ActivitySpec spec : specs) {
                ScheduledActivity a = new ScheduledActivity();
                a.setSubject(subject);
                a.setProfessor(professor);
                a.setActivityType(spec.type());
                a.setWeekParity(spec.parity());
                a.setRequiresAmphitheater(spec.requiresAmphitheater());
                a.setSpecialCategory(spec.category());
                a.setRawType(activitate);
                a.setDurationInSlots(1);
                a.setStudentGroups(new LinkedHashSet<>(groups));
                activityRepo.save(a);
                activities++;
            }
        }
        result.setSubjects(subjects);
        result.setActivities(activities);
    }

    // ------------------------------------------------------------------ helpers

    /** Resolves a set_studenti string ("AP1+SP1+RISE1", "RISE3 - Grupa 1+...", "-") to groups. */
    private Set<StudentGroup> resolveGroups(String setStudenti, Map<String, StudentGroup> byName,
                                            Map<String, List<StudentGroup>> bySpecYear,
                                            ImportResult result, int excelRow) {
        Set<StudentGroup> groups = new LinkedHashSet<>();
        if (setStudenti == null || setStudenti.equals("-") || setStudenti.isBlank()) {
            return groups; // faculty-wide / no specific audience (e.g. DCT)
        }
        for (String rawToken : setStudenti.split("\\+")) {
            String token = rawToken.trim();
            if (token.isEmpty() || token.equals("-")) {
                continue;
            }
            String specYearKey = token.replaceAll("\\s+", "").toUpperCase();
            boolean bare = specYearKey.matches("[A-Z]+[0-9]+");
            if (bare && bySpecYear.containsKey(specYearKey)) {
                groups.addAll(bySpecYear.get(specYearKey));
            } else if (byName.containsKey(token)) {
                groups.add(byName.get(token));
            } else if (bySpecYear.containsKey(specYearKey)) {
                groups.addAll(bySpecYear.get(specYearKey));
            } else {
                // The Discipline sheet references a group/section not declared in Sectii (real data
                // gap, e.g. "SSEC3"). Rather than blocking the whole import, auto-create a placeholder
                // group with a default size and surface a loud warning so it can be corrected.
                StudentGroup placeholder = createPlaceholderGroup(token, specYearKey, byName, bySpecYear);
                groups.add(placeholder);
                result.addWarning(SHEET_SUBJECTS, excelRow,
                        "Group '" + token + "' is not in Sectii — auto-created placeholder (size "
                                + PLACEHOLDER_GROUP_SIZE + "). Add it to Sectii for accurate capacity.");
            }
        }
        return groups;
    }

    /** Creates, persists and registers a placeholder professor for a name missing from Profesori. */
    private Professor createPlaceholderProfessor(String name, Map<String, Professor> byName) {
        Professor p = new Professor();
        p.setName(name.trim());
        professorRepo.save(p);
        byName.put(normalizeName(name), p);
        return p;
    }

    /** Default size assumed for a group referenced in Discipline but missing from Sectii. */
    private static final int PLACEHOLDER_GROUP_SIZE = 30;

    /** Creates, persists and registers a placeholder group for an unknown set_studenti token. */
    private StudentGroup createPlaceholderGroup(String token, String specYearKey,
                                                Map<String, StudentGroup> byName,
                                                Map<String, List<StudentGroup>> bySpecYear) {
        var m = java.util.regex.Pattern.compile("([A-Za-z]+)\\s*([0-9]+)").matcher(token);
        String spec = token;
        int year = 1;
        if (m.find()) {
            spec = m.group(1);
            year = Integer.parseInt(m.group(2));
        }
        StudentGroup g = new StudentGroup();
        g.setName(token);
        g.setSpecialization(spec);
        g.setYear(year);
        g.setStudyProgram(StudyProgram.LICENSE);
        g.setStudentCount(PLACEHOLDER_GROUP_SIZE);
        groupRepo.save(g);
        byName.put(token, g);
        bySpecYear.computeIfAbsent(specYearKey, k -> new ArrayList<>()).add(g);
        return g;
    }

    /** Maps Romanian day abbreviations "L,M,Mi,J,V" to DayOfWeek. */
    static List<DayOfWeek> parseDays(String raw) {
        List<DayOfWeek> days = new ArrayList<>();
        if (raw == null) {
            return List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        for (String t : raw.split(",")) {
            switch (t.trim().toLowerCase()) {
                case "l", "lu", "luni" -> days.add(DayOfWeek.MONDAY);
                case "m", "ma", "marti", "marți" -> days.add(DayOfWeek.TUESDAY);
                case "mi", "miercuri" -> days.add(DayOfWeek.WEDNESDAY);
                case "j", "joi" -> days.add(DayOfWeek.THURSDAY);
                case "v", "vi", "vineri" -> days.add(DayOfWeek.FRIDAY);
                default -> { /* ignore unknown token */ }
            }
        }
        return days;
    }

    /**
     * Normalizes a name for matching: strip diacritics, lowercase, then sort the name tokens so the
     * order does not matter. The Profesori and Discipline sheets often list names in opposite order
     * ("Corina Tursie" vs "Tursie Corina"); sorting tokens makes both resolve to the same key.
     */
    static String normalizeName(String name) {
        String n = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[.,]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
        String[] tokens = n.split(" ");
        java.util.Arrays.sort(tokens);
        return String.join(" ", tokens);
    }

    // Silence unused-field warning for ActivityType import (kept for readability of mappings).
    @SuppressWarnings("unused")
    private static final ActivityType[] ALL_TYPES = ActivityType.values();
}
