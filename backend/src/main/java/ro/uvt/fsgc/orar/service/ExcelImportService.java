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
import ro.uvt.fsgc.orar.domain.WeekParity;
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
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

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
    private final TimeSlotRepository timeSlotRepo;

    public ExcelImportService(StudentGroupRepository groupRepo, ProfessorRepository professorRepo,
                              RoomRepository roomRepo, SubjectRepository subjectRepo,
                              ScheduledActivityRepository activityRepo, BuildingRepository buildingRepo,
                              SpecialBlockRuleRepository specialBlockRepo,
                              ProfessorUnavailabilityRepository profUnavailRepo,
                              ProfessorRoomRestrictionRepository profRoomRepo,
                              BlockedDayRuleRepository blockedDayRepo,
                              TimeSlotRepository timeSlotRepo) {
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
        this.timeSlotRepo = timeSlotRepo;
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
        // Rooms are deliberately NOT deleted. They are matched by name and updated in
        // parseRooms instead, because deleting a room takes its unavailability windows with it
        // (DB-level ON DELETE CASCADE) and those are entered by hand, one interval at a time.
        // A room the sheet does not mention is left alone; remove it from Administrare instead.
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
                StudentGroup other = byName.get(name);
                // A cod_grupa that belongs to another YEAR of the same section is a typo, not a
                // real duplicate ("CRP I_gr.2" on a CRP year 2 row means "CRP II_gr.2"). Dropping
                // the row would leave that year one group short and double its seminars up on the
                // group that remains, so the group is renamed after its siblings instead. Nothing
                // refers to a group by this code — set_studenti names the year — so it is safe.
                String repaired = sameSectionOtherYear(other, spec, year)
                        ? repairedGroupName(name, bySpecYear.get(specYearKey), spec, year,
                                specYearCount, specYearKey)
                        : null;
                if (repaired == null || byName.containsKey(repaired)) {
                    result.addWarning(SHEET_SECTIONS, excelRow, "Group '" + name + "' is already used by "
                            + other.getSpecialization() + " year " + other.getYear() + ", so this row ("
                            + spec + " year " + year + ") was SKIPPED and that year is left with one"
                            + " group fewer — its seminars will double up on another group. Give each"
                            + " group its own cod_grupa.");
                    continue;
                }
                result.addWarning(SHEET_SECTIONS, excelRow, "cod_grupa '" + name + "' belongs to "
                        + other.getSpecialization() + " year " + other.getYear() + ", but this row is "
                        + spec + " year " + year + " — read as a typo and imported as '" + repaired
                        + "'. Fix the cell if that is not the group you meant.");
                name = repaired;
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

    /**
     * Upserts the rooms by name. An existing room keeps its identity — and therefore its
     * unavailability windows — and only has its columns refreshed; a room missing from the sheet
     * is left untouched rather than deleted.
     */
    private void parseRooms(Sheet sheet, ImportResult result) {
        int saved = 0;
        int updated = 0;
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
            Room existing = roomRepo.findByName(name).orElse(null);
            if (existing == null) {
                // "028" in the app and "28" in the sheet are the same room to a human but two
                // rows to the database, and the new one arrives without the unavailabilities that
                // were configured by hand. Say so rather than quietly creating a near-duplicate.
                roomRepo.findAll().stream()
                        .filter(other -> sameRoomLoosely(other.getName(), name))
                        .findFirst()
                        .ifPresent(twin -> result.addWarning(SHEET_ROOMS, excelRow,
                                "Room '" + name + "' looks like the existing '" + twin.getName()
                                        + "' but the names differ, so a second room was created."
                                        + " Rename one of them so they match, or the configured"
                                        + " unavailabilities will not apply to this one."));
            }
            Room room = existing != null ? existing : new Room();
            if (existing != null) {
                updated++;
                // Re-imported windows replace the previous ones; the unavailabilities are a
                // separate collection and stay as configured.
                room.getAvailabilities().clear();
            } else {
                saved++;
            }
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

            // Rooms are available for the whole teaching day by default; the Excel's
            // available_from/available_until are no longer applied, because narrowing them here
            // silently hid the last module (the file says 20:00, module 8 ends at 21:10).
            // Exceptions are recorded as room unavailabilities in Constrangeri instead.
            List<DayOfWeek> days = parseDays(ExcelCells.str(row, 5));
            LocalTime from = teachingDayStart();
            LocalTime until = teachingDayEnd();
            for (DayOfWeek d : days) {
                room.getAvailabilities().add(new RoomAvailability(room, d, from, until));
                avail++;
            }
            roomRepo.save(room);
        }
        long kept = roomRepo.count() - saved - updated;
        if (saved == 0 && updated == 0) {
            result.addWarning(SHEET_ROOMS, 1, "Sheet has no room rows. The " + roomRepo.count()
                    + " existing rooms and their unavailabilities were kept untouched.");
        } else if (kept > 0) {
            result.addWarning(SHEET_ROOMS, 1, kept + " room(s) already in the app are not in this"
                    + " sheet and were left untouched. Delete them in Administrare if obsolete.");
        }
        result.setRooms(saved + updated);
        result.setRoomAvailabilities(avail);
    }

    // ------------------------------------------------------------------ Discipline

    private void parseSubjects(Sheet sheet, ImportResult result, Map<String, Professor> professorByName,
                               Map<String, StudentGroup> groupByName,
                               Map<String, List<StudentGroup>> groupBySpecYear) {
        // First pass: decide, per row, which single group it is really about and which discipline
        // it belongs to. The sheet expresses that in two contradictory ways (see RowPlan), and both
        // answers need the whole sheet in view, so they cannot be made row by row below.
        Map<Integer, RowPlan> plans = planRows(sheet, result, groupByName, groupBySpecYear);

        Map<String, Subject> subjectByCode = new HashMap<>();
        Map<String, Subject> subjectByDiscipline = new HashMap<>();
        int subjects = 0;
        int activities = 0;
        int mergedByName = 0;
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

            // Resolve student groups from set_studenti, keeping cell order and any (SI)/(SP) marker.
            List<GroupRef> groupRefs = resolveGroupRefs(setStudenti, groupByName, groupBySpecYear,
                    result, excelRow);

            RowPlan plan = plans.get(excelRow);
            // "Genuri ... Grupa 1" is not a discipline of its own: it is the seminar of "Genuri ..."
            // held for group 1. Such a row joins the parent discipline instead of creating a twin.
            String disciplineName = plan == null ? materie : plan.disciplineName();
            String disciplineKey = plan == null ? null : plan.disciplineKey();

            Subject subject = disciplineKey == null ? null : subjectByDiscipline.get(disciplineKey);
            if (subject != null && !subject.getCode().equals(codMaterie)) {
                mergedByName++;
            }
            if (subject == null) {
                subject = subjectByCode.get(codMaterie);
            }
            if (subject == null) {
                subject = new Subject();
                subject.setCode(codMaterie);
                subject.setName(disciplineName);
                subject.setDepartment(dept);
                subjectRepo.save(subject);
                subjectByCode.put(codMaterie, subject);
                subjects++;
            }
            if (disciplineKey != null) {
                subjectByDiscipline.putIfAbsent(disciplineKey, subject);
            }

            // Halves of one alternating hour share a key so the solver keeps them together.
            boolean paired = specs.size() > 1 && splitsAudienceByParity(specs, groupRefs);
            String pairKey = specs.size() > 1 ? codMaterie + "#" + excelRow : null;
            if (paired) {
                result.addWarning(SHEET_SUBJECTS, excelRow,
                        "Alternating hour '" + activitate + "': split " + groupRefs.size()
                                + " groups one per half (" + describePairing(specs, groupRefs)
                                + "). Add (SI)/(SP) after a group name in set_studenti to choose"
                                + " the pairing explicitly.");
            }

            for (int i = 0; i < specs.size(); i++) {
                ActivityTypeParser.ActivitySpec spec = specs.get(i);
                Set<StudentGroup> audience = audienceFor(spec, i, specs, groupRefs, paired);
                // The row is about one group only: take it, and do not split the year all over again.
                List<Set<StudentGroup>> perGroup = plan != null && plan.onlyGroup() != null
                        ? List.of(new LinkedHashSet<>(List.of(plan.onlyGroup())))
                        : splitPerGroup(spec.type(), audience);
                if (perGroup.size() > 1) {
                    result.addWarning(SHEET_SUBJECTS, excelRow,
                            "'" + activitate + "' is taught per group: created " + perGroup.size()
                                    + " separate activities (" + describeGroups(perGroup)
                                    + "), each needing its own slot.");
                }
                for (Set<StudentGroup> oneAudience : perGroup) {
                    ScheduledActivity a = new ScheduledActivity();
                    a.setSubject(subject);
                    a.setProfessor(professor);
                    a.setActivityType(spec.type());
                    a.setWeekParity(spec.parity());
                    a.setRequiresAmphitheater(spec.requiresAmphitheater());
                    a.setSpecialCategory(spec.category());
                    a.setRawType(activitate);
                    a.setDurationInSlots(1);
                    a.setParityPairKey(pairKey);
                    a.setStudentGroups(oneAudience);
                    activityRepo.save(a);
                    activities++;
                }
            }
        }
        if (mergedByName > 0) {
            result.addWarning(SHEET_SUBJECTS, 1, mergedByName + " row(s) named a group in the"
                    + " subject name (\"... Grupa N\"): each was attached to that one group and"
                    + " folded into its parent discipline, instead of becoming a discipline of its"
                    + " own repeated for every group.");
        }
        result.setSubjects(subjects);
        result.setActivities(activities);
    }

    // ------------------------------------------------ which group, and which discipline, per row

    /**
     * What one Discipline row really means, once the whole sheet is in view.
     *
     * <p>The sheet says "one activity per group" in two different ways, and both need more than the
     * row itself to read. Either the group is written into the subject name ("... Grupa 2") while
     * set_studenti still names the whole year — then the name wins and the row is that one group's
     * seminar, belonging to the discipline without the suffix. Or nothing marks the group and the
     * discipline simply has as many seminar rows as the year has groups — then they are matched in
     * order, first row to first group.
     *
     * @param disciplineName the subject name without any "Grupa N" suffix
     * @param disciplineKey  identifies the discipline across its rows (name + audience)
     * @param onlyGroup      the single group this row is for, or null to keep the default split
     */
    record RowPlan(String disciplineName, String disciplineKey, StudentGroup onlyGroup) {
    }

    private Map<Integer, RowPlan> planRows(Sheet sheet, ImportResult result,
                                           Map<String, StudentGroup> groupByName,
                                           Map<String, List<StudentGroup>> groupBySpecYear) {
        // Group resolution warnings belong to the real pass, not to this dry run.
        ImportResult silent = new ImportResult();
        Map<Integer, RowPlan> plans = new HashMap<>();
        // rows of one discipline that are taught per group and name no group: key -> Excel rows
        Map<String, List<Integer>> unmarkedRows = new java.util.LinkedHashMap<>();
        Map<String, List<StudentGroup>> audienceOfKey = new HashMap<>();
        Map<String, Set<StudentGroup>> takenOfKey = new HashMap<>();
        Map<String, String> nameOfKey = new HashMap<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (ExcelCells.isBlank(row)) {
                continue;
            }
            int excelRow = r + 1;
            String setStudenti = ExcelCells.str(row, 0);
            String materie = ExcelCells.str(row, 1);
            String codMaterie = ExcelCells.str(row, 2);
            String activitate = ExcelCells.str(row, 4);
            if (materie == null || codMaterie == null) {
                continue;
            }
            List<ActivityTypeParser.ActivitySpec> specs = ActivityTypeParser.parse(activitate);
            if (specs.isEmpty()) {
                continue;
            }
            List<StudentGroup> groups = resolveGroupRefs(setStudenti, groupByName, groupBySpecYear,
                    silent, excelRow).stream().map(GroupRef::group).toList();
            String base = stripGroupSuffix(materie);
            String key = disciplineKey(base, groups);
            nameOfKey.putIfAbsent(key, base);

            Integer named = groupNumberInName(materie);
            if (named != null) {
                StudentGroup hit = groupNumbered(groups, named);
                if (hit == null) {
                    result.addWarning(SHEET_SUBJECTS, excelRow, "Subject '" + materie + "' names"
                            + " group " + named + ", but set_studenti '" + setStudenti + "' has no"
                            + " such group (" + describeNames(groups) + ") — the row falls back on"
                            + " the groups it does have, doubling up on one of them. Usually that"
                            + " group is missing or misnamed in the Sectii sheet.");
                } else {
                    takenOfKey.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(hit);
                }
                plans.put(excelRow, new RowPlan(base, key, hit));
                continue;
            }
            plans.put(excelRow, new RowPlan(base, key, null));
            // a course is attended by everyone: only per-group rows can be matched in order
            if (specs.size() == 1 && specs.get(0).type() != ActivityType.COURSE && groups.size() > 1) {
                unmarkedRows.computeIfAbsent(key, k -> new ArrayList<>()).add(excelRow);
                audienceOfKey.put(key, groups);
            }
        }

        // As many unnamed rows as there are groups still free: match them in sheet order. Rows
        // whose name already claimed a group are out of the running, and so are their groups.
        for (Map.Entry<String, List<Integer>> e : unmarkedRows.entrySet()) {
            List<Integer> rows = e.getValue();
            List<StudentGroup> groups = audienceOfKey.get(e.getKey());
            if (groups == null) {
                continue;
            }
            Set<StudentGroup> taken = takenOfKey.getOrDefault(e.getKey(), Set.of());
            List<StudentGroup> free = groups.stream().filter(g -> !taken.contains(g)).toList();
            if (rows.size() != free.size()) {
                continue; // cannot tell which row is which group: leave the default split
            }
            if (rows.size() == 1 && taken.isEmpty()) {
                continue; // one row for one group: nothing to decide
            }
            StringBuilder how = new StringBuilder();
            for (int i = 0; i < rows.size(); i++) {
                RowPlan old = plans.get(rows.get(i));
                plans.put(rows.get(i), new RowPlan(old.disciplineName(), old.disciplineKey(),
                        free.get(i)));
                how.append(i == 0 ? "" : ", ").append("row ").append(rows.get(i)).append(" -> ")
                        .append(free.get(i).getName());
            }
            result.addWarning(SHEET_SUBJECTS, rows.get(0), "'" + nameOfKey.get(e.getKey()) + "': "
                    + rows.size() + " row(s) taught per group name no group, and " + free.size()
                    + " of " + groups.size() + " group(s) are still free — matched in sheet order ("
                    + how + "). Check the order is right, or name the group in set_studenti.");
        }
        return plans;
    }

    /** "MD II_gr.1, MD II_gr.2" — the groups an audience actually resolved to. */
    private static String describeNames(List<StudentGroup> groups) {
        return groups.isEmpty() ? "none"
                : String.join(", ", groups.stream().map(StudentGroup::getName).toList());
    }

    /** Identifies a discipline across its rows: base name + the year(s) it is taught to. */
    private static String disciplineKey(String baseName, List<StudentGroup> groups) {
        List<String> years = groups.stream()
                .map(g -> g.getSpecialization() + g.getYear())
                .distinct().sorted().toList();
        return normalizeName(baseName) + "|" + String.join("+", years);
    }

    /** "Genuri ... Grupa 2" -> 2; null when the name does not end in a group number. */
    static Integer groupNumberInName(String subjectName) {
        if (subjectName == null) {
            return null;
        }
        var m = GROUP_SUFFIX.matcher(subjectName.trim());
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** "Genuri ... Grupa 2" -> "Genuri ...". */
    static String stripGroupSuffix(String subjectName) {
        if (subjectName == null) {
            return null;
        }
        return GROUP_SUFFIX.matcher(subjectName.trim()).replaceAll("").trim();
    }

    /** The one group of the audience whose name ends in that number ("MD II_gr.2" -> 2). */
    static StudentGroup groupNumbered(List<StudentGroup> groups, int number) {
        StudentGroup found = null;
        for (StudentGroup g : groups) {
            var m = TRAILING_NUMBER.matcher(g.getName() == null ? "" : g.getName().trim());
            if (m.find() && Integer.parseInt(m.group(1)) == number) {
                if (found != null) {
                    return null; // ambiguous: two groups claim the same number
                }
                found = g;
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ helpers

    /** "... Grupa 2", "... gr.2", "... - Grupa 2" at the end of a subject name. */
    private static final java.util.regex.Pattern GROUP_SUFFIX = java.util.regex.Pattern.compile(
            "\\s*[-–]?\\s*\\bgr(?:upa)?\\.?\\s*(\\d+)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** The number a group name ends with ("MD II_gr.2", "RISE1 - Grupa 2" -> 2). */
    private static final java.util.regex.Pattern TRAILING_NUMBER =
            java.util.regex.Pattern.compile("(\\d+)\\s*$");

    /** True when a taken group name comes from another year of the same section. */
    private static boolean sameSectionOtherYear(StudentGroup taken, String spec, int year) {
        return taken.getSpecialization() != null && spec != null
                && taken.getSpecialization().trim().equalsIgnoreCase(spec.trim())
                && taken.getYear() != year;
    }

    /**
     * The name a mistyped cod_grupa should have had. Siblings of the same section-year show the
     * house style ("CRP II_gr.1" -> "CRP II_gr.2"); with no sibling yet, the group is the year's
     * first and takes the same name an empty cod_grupa would have produced.
     */
    static String repairedGroupName(String typo, List<StudentGroup> siblings, String spec,
                                            int year, Map<String, Integer> specYearCount,
                                            String specYearKey) {
        var m = TRAILING_NUMBER.matcher(typo.trim());
        if (siblings != null && !siblings.isEmpty() && m.find()) {
            String pattern = siblings.get(0).getName();
            var sm = TRAILING_NUMBER.matcher(pattern.trim());
            if (sm.find()) {
                return pattern.substring(0, sm.start(1)) + m.group(1) + pattern.substring(sm.end(1));
            }
            return null;
        }
        int n = specYearCount.merge(specYearKey, 1, Integer::sum);
        return (n == 1) ? (spec + year) : (spec + year + " - Grupa " + n);
    }

    /** A resolved group plus the explicit (SI)/(SP) marker its token carried, if any. */
    record GroupRef(StudentGroup group, WeekParity parity) {
    }

    /**
     * Resolves a set_studenti string ("AP1+SP1+RISE1", "RISE3 - Grupa 1+...", "-") to groups,
     * preserving the order they appear in the cell and any per-group (SI)/(SP) marker. Order
     * matters: for an alternating hour written without markers, the first group takes the first
     * half. Duplicates are dropped, keeping the first occurrence.
     */
    private List<GroupRef> resolveGroupRefs(String setStudenti, Map<String, StudentGroup> byName,
                                            Map<String, List<StudentGroup>> bySpecYear,
                                            ImportResult result, int excelRow) {
        List<GroupRef> refs = new ArrayList<>();
        Set<StudentGroup> seen = new LinkedHashSet<>();
        if (setStudenti == null || setStudenti.equals("-") || setStudenti.isBlank()) {
            return refs; // faculty-wide / no specific audience (e.g. DCT)
        }
        for (String rawToken : setStudenti.split("\\+")) {
            ActivityTypeParser.GroupToken parsed = ActivityTypeParser.parseGroupToken(rawToken);
            String token = parsed.name();
            if (token.isEmpty() || token.equals("-")) {
                continue;
            }
            List<StudentGroup> resolved = new ArrayList<>();
            String specYearKey = token.replaceAll("\\s+", "").toUpperCase();
            boolean bare = specYearKey.matches("[A-Z]+[0-9]+");
            if (bare && bySpecYear.containsKey(specYearKey)) {
                resolved.addAll(bySpecYear.get(specYearKey));
            } else if (byName.containsKey(token)) {
                resolved.add(byName.get(token));
            } else if (bySpecYear.containsKey(specYearKey)) {
                resolved.addAll(bySpecYear.get(specYearKey));
            } else {
                // The Discipline sheet references a group/section not declared in Sectii (real data
                // gap, e.g. "SSEC3"). Rather than blocking the whole import, auto-create a placeholder
                // group with a default size and surface a loud warning so it can be corrected.
                StudentGroup placeholder = createPlaceholderGroup(token, specYearKey, byName, bySpecYear);
                resolved.add(placeholder);
                result.addWarning(SHEET_SUBJECTS, excelRow,
                        "Group '" + token + "' is not in Sectii — auto-created placeholder (size "
                                + PLACEHOLDER_GROUP_SIZE + "). Add it to Sectii for accurate capacity.");
            }
            for (StudentGroup g : resolved) {
                if (seen.add(g)) {
                    refs.add(new GroupRef(g, parsed.parity()));
                }
            }
        }
        return refs;
    }

    /**
     * True when the halves of a combined activitate value should each take part of the audience
     * instead of all of it. Explicit (SI)/(SP) markers on the groups always decide; without them,
     * only a same-type alternating value ("Seminar(SI)/Seminar(SP)") with exactly as many groups
     * as halves is split, one group per half. Anything else — notably "Curs(SI)/Seminar(SP)",
     * where the same audience alternates between a course and a seminar — keeps every group on
     * every half.
     */
    static boolean splitsAudienceByParity(List<ActivityTypeParser.ActivitySpec> specs,
                                          List<GroupRef> refs) {
        if (specs.size() < 2 || refs.isEmpty()) {
            return false;
        }
        if (refs.stream().anyMatch(r -> r.parity() != null)) {
            return true;
        }
        return ActivityTypeParser.isAlternatingSameType(specs) && refs.size() == specs.size();
    }

    /** The groups attending one half of a (possibly alternating) activitate value. */
    static Set<StudentGroup> audienceFor(ActivityTypeParser.ActivitySpec spec, int index,
                                         List<ActivityTypeParser.ActivitySpec> specs,
                                         List<GroupRef> refs, boolean paired) {
        if (!paired) {
            return refs.stream().map(GroupRef::group)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        if (refs.stream().anyMatch(r -> r.parity() != null)) {
            // A marked group attends only its own half; an unmarked one attends every half.
            return refs.stream()
                    .filter(r -> r.parity() == null || r.parity() == spec.parity())
                    .map(GroupRef::group)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        // No markers: pair by position, so the first group in the cell takes the first half.
        return index < refs.size()
                ? new LinkedHashSet<>(List.of(refs.get(index).group()))
                : new LinkedHashSet<>();
    }

    /**
     * Splits an audience into the activities actually held. A seminar or lab is taught once per
     * group — the groups exist precisely so practical work happens in a room of 25-30 students, and
     * each group gets its own slot, which is how the real timetable shows it ("s, gr. 1" and
     * "s, gr. 2" on different days). A course is attended by all its groups together, so it stays
     * a single activity.
     *
     * <p>An audience already divided by parity arrives here with one group per half, so an
     * alternating "Seminar(SI)/Seminar(SP)" is left alone: there the two groups share one weekly
     * hour instead of getting one each.
     */
    static List<Set<StudentGroup>> splitPerGroup(ActivityType type, Set<StudentGroup> audience) {
        if (type == ActivityType.COURSE || audience.size() <= 1) {
            return List.of(audience);
        }
        List<Set<StudentGroup>> perGroup = new ArrayList<>();
        for (StudentGroup g : audience) {
            perGroup.add(new LinkedHashSet<>(List.of(g)));
        }
        return perGroup;
    }

    /** "RISE1 - Grupa 1 | RISE1 - Grupa 2", for the import report. */
    private static String describeGroups(List<Set<StudentGroup>> perGroup) {
        List<String> names = new ArrayList<>();
        for (Set<StudentGroup> one : perGroup) {
            one.forEach(g -> names.add(g.getName()));
        }
        return String.join(" | ", names);
    }

    /** "RISE1 - Grupa 1 -> SI, RISE1 - Grupa 2 -> SP", for the import report. */
    private static String describePairing(List<ActivityTypeParser.ActivitySpec> specs,
                                         List<GroupRef> refs) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            for (StudentGroup g : audienceFor(specs.get(i), i, specs, refs, true)) {
                parts.add(g.getName() + " -> " + shortParity(specs.get(i).parity()));
            }
        }
        return String.join(", ", parts);
    }

    private static String shortParity(WeekParity p) {
        return p == WeekParity.ODD_WEEKS ? "SI" : p == WeekParity.EVEN_WEEKS ? "SP" : "toate";
    }

    /** Same room to a human: ignoring case, surrounding spaces and leading zeros ("028" = "28"). */
    private static boolean sameRoomLoosely(String a, String b) {
        return loosenRoomName(a).equals(loosenRoomName(b));
    }

    private static String loosenRoomName(String n) {
        String v = n == null ? "" : n.trim().toUpperCase().replaceAll("\\s+", "");
        String stripped = v.replaceFirst("^0+", "");
        return stripped.isEmpty() ? v : stripped;
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

    /** Start of the first module, read from the seeded grid rather than hard-coded. */
    private LocalTime teachingDayStart() {
        return timeSlotRepo.findAll().stream()
                .map(ro.uvt.fsgc.orar.domain.TimeSlot::getStartTime)
                .min(LocalTime::compareTo)
                .orElse(LocalTime.of(8, 0));
    }

    /** End of the last module, read from the seeded grid rather than hard-coded. */
    private LocalTime teachingDayEnd() {
        return timeSlotRepo.findAll().stream()
                .map(ro.uvt.fsgc.orar.domain.TimeSlot::getEndTime)
                .max(LocalTime::compareTo)
                .orElse(LocalTime.of(21, 10));
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
