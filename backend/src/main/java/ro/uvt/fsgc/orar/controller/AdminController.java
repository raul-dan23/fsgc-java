package ro.uvt.fsgc.orar.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.WeekParity;
import ro.uvt.fsgc.orar.repository.ProfessorRepository;
import ro.uvt.fsgc.orar.repository.SpecialBlockRuleRepository;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.StudentGroupRepository;
import ro.uvt.fsgc.orar.repository.SubjectRepository;

/**
 * CRUD for the base data that normally arrives via the Excel import (groups, professors, rooms,
 * subjects, activities), so it can be corrected in the UI without re-importing the whole workbook.
 *
 * <p>Delete policy: a reference that is <em>source data</em> (an activity's subject, professor or
 * attending groups) blocks the delete and reports what points at the row. A reference that is only
 * a <em>solver assignment</em> (an activity's room) is cleared instead, since it is regenerated on
 * the next solve. Unique-key collisions come back as 409 with a readable message rather than a 500.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin
public class AdminController {

    private final ProfessorRepository professorRepo;
    private final RoomRepository roomRepo;
    private final StudentGroupRepository groupRepo;
    private final SubjectRepository subjectRepo;
    private final ScheduledActivityRepository activityRepo;
    private final SpecialBlockRuleRepository specialBlockRepo;

    public AdminController(ProfessorRepository professorRepo, RoomRepository roomRepo,
                           StudentGroupRepository groupRepo, SubjectRepository subjectRepo,
                           ScheduledActivityRepository activityRepo,
                           SpecialBlockRuleRepository specialBlockRepo) {
        this.professorRepo = professorRepo;
        this.roomRepo = roomRepo;
        this.groupRepo = groupRepo;
        this.subjectRepo = subjectRepo;
        this.activityRepo = activityRepo;
        this.specialBlockRepo = specialBlockRepo;
    }

    /** Signals a refused write (missing row, blocking reference, duplicate key) as HTTP 409. */
    static class ConflictException extends RuntimeException {
        ConflictException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, String>> onConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> onIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                "Valoare duplicată sau invalidă — numele/codul trebuie să fie unic."));
    }

    // ------------------------------------------------------------------ summary

    /** Row counts per table, for the admin landing tiles. */
    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of(
                "groups", groupRepo.count(),
                "professors", professorRepo.count(),
                "rooms", roomRepo.count(),
                "subjects", subjectRepo.count(),
                "activities", activityRepo.count());
    }

    // ------------------------------------------------------------------ professors

    public record ProfessorReq(String name, String email, String title, String department,
                               Boolean hasOwnLaptop) {
    }

    @PostMapping("/professors")
    public Professor createProfessor(@RequestBody ProfessorReq req) {
        return saveProfessor(new Professor(), req);
    }

    @PutMapping("/professors/{id}")
    public Professor updateProfessor(@PathVariable Long id, @RequestBody ProfessorReq req) {
        return saveProfessor(professorRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Profesorul nu există (id " + id + ")")), req);
    }

    private Professor saveProfessor(Professor p, ProfessorReq req) {
        String name = trimmed(req.name());
        if (name == null) {
            throw new ConflictException("Numele profesorului este obligatoriu.");
        }
        p.setName(name);
        p.setEmail(trimmed(req.email()));
        p.setTitle(trimmed(req.title()));
        p.setDepartment(trimmed(req.department()));
        p.setHasOwnLaptop(req.hasOwnLaptop() == null || req.hasOwnLaptop());
        return professorRepo.save(p);
    }

    @DeleteMapping("/professors/{id}")
    @Transactional
    public ResponseEntity<Void> deleteProfessor(@PathVariable Long id) {
        Professor p = professorRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Profesorul nu există (id " + id + ")"));
        long used = activityRepo.findAll().stream()
                .filter(a -> a.getProfessor() != null && a.getProfessor().getId().equals(id))
                .count();
        if (used > 0) {
            throw new ConflictException("Profesorul „" + p.getName() + "” este folosit de " + used
                    + " activități. Schimbă-le profesorul întâi.");
        }
        professorRepo.delete(p);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ rooms

    public record RoomReq(String name, String department, String floor, Integer capacity,
                          String typology, String usageRestrictions) {
    }

    @PostMapping("/rooms")
    public Room createRoom(@RequestBody RoomReq req) {
        return saveRoom(new Room(), req);
    }

    @PutMapping("/rooms/{id}")
    public Room updateRoom(@PathVariable Long id, @RequestBody RoomReq req) {
        return saveRoom(roomRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Sala nu există (id " + id + ")")), req);
    }

    private Room saveRoom(Room room, RoomReq req) {
        String name = trimmed(req.name());
        if (name == null) {
            throw new ConflictException("Numele sălii este obligatoriu.");
        }
        if (req.capacity() == null || req.capacity() <= 0) {
            throw new ConflictException("Capacitatea sălii „" + name + "” trebuie să fie > 0.");
        }
        room.setName(name);
        room.setDepartment(trimmed(req.department()));
        room.setFloor(trimmed(req.floor()));
        room.setCapacity(req.capacity());
        room.setTypology(RoomTypology.fromExcel(req.typology()));
        room.setUsageRestrictions(trimmed(req.usageRestrictions()));
        return roomRepo.save(room);
    }

    /** Clears the room off any activity that is merely assigned to it, then deletes the room. */
    @DeleteMapping("/rooms/{id}")
    @Transactional
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        Room room = roomRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Sala nu există (id " + id + ")"));
        List<ScheduledActivity> assigned = activityRepo.findAll().stream()
                .filter(a -> a.getRoom() != null && a.getRoom().getId().equals(id))
                .toList();
        for (ScheduledActivity a : assigned) {
            a.setRoom(null);
        }
        activityRepo.saveAll(assigned);
        activityRepo.flush();
        roomRepo.delete(room);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ student groups

    public record GroupReq(String name, String specialization, Integer year, String studyProgram,
                           Integer studentCount, String department) {
    }

    @PostMapping("/groups")
    public StudentGroup createGroup(@RequestBody GroupReq req) {
        return saveGroup(new StudentGroup(), req);
    }

    @PutMapping("/groups/{id}")
    public StudentGroup updateGroup(@PathVariable Long id, @RequestBody GroupReq req) {
        return saveGroup(groupRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Grupa nu există (id " + id + ")")), req);
    }

    private StudentGroup saveGroup(StudentGroup g, GroupReq req) {
        String name = trimmed(req.name());
        String spec = trimmed(req.specialization());
        if (name == null || spec == null) {
            throw new ConflictException("Numele grupei și secția sunt obligatorii.");
        }
        if (req.studentCount() == null || req.studentCount() <= 0) {
            throw new ConflictException("Numărul de studenți al grupei „" + name + "” trebuie să fie > 0.");
        }
        StudyProgram program = StudyProgram.fromExcel(req.studyProgram());
        g.setName(name);
        g.setSpecialization(spec);
        g.setYear(req.year() == null ? 1 : req.year());
        g.setStudyProgram(program == null ? StudyProgram.LICENSE : program);
        g.setStudentCount(req.studentCount());
        g.setDepartment(trimmed(req.department()));
        return groupRepo.save(g);
    }

    @DeleteMapping("/groups/{id}")
    @Transactional
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        StudentGroup g = groupRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Grupa nu există (id " + id + ")"));
        long used = activityRepo.findAll().stream()
                .filter(a -> a.getStudentGroups().stream().anyMatch(x -> x.getId().equals(id)))
                .count();
        if (used > 0) {
            throw new ConflictException("Grupa „" + g.getName() + "” participă la " + used
                    + " activități. Scoate-o din ele întâi.");
        }
        groupRepo.delete(g);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ subjects

    public record SubjectReq(String code, String name, String department) {
    }

    @PostMapping("/subjects")
    public Subject createSubject(@RequestBody SubjectReq req) {
        return saveSubject(new Subject(), req);
    }

    @PutMapping("/subjects/{id}")
    public Subject updateSubject(@PathVariable Long id, @RequestBody SubjectReq req) {
        return saveSubject(subjectRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Disciplina nu există (id " + id + ")")), req);
    }

    private Subject saveSubject(Subject s, SubjectReq req) {
        String code = trimmed(req.code());
        String name = trimmed(req.name());
        if (code == null || name == null) {
            throw new ConflictException("Codul și denumirea disciplinei sunt obligatorii.");
        }
        s.setCode(code);
        s.setName(name);
        s.setDepartment(trimmed(req.department()));
        return subjectRepo.save(s);
    }

    @DeleteMapping("/subjects/{id}")
    @Transactional
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        Subject s = subjectRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Disciplina nu există (id " + id + ")"));
        long used = activityRepo.findAll().stream()
                .filter(a -> a.getSubject().getId().equals(id))
                .count();
        if (used > 0) {
            throw new ConflictException("Disciplina „" + s.getName() + "” are " + used
                    + " activități. Șterge-le întâi.");
        }
        subjectRepo.delete(s);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ activities

    /** Editable projection of an activity: ids for the references, names for display. */
    public record AdminActivity(Long id, Long subjectId, String subjectCode, String subjectName,
                                Long professorId, String professorName, String activityType,
                                String weekParity, String specialCategory, boolean requiresAmphitheater,
                                boolean requiresLab, boolean online, boolean pinned, String rawType,
                                int durationInSlots, List<Long> groupIds,
                                List<String> groupNames, int students, String room, String day,
                                Integer slotIndex, String parityPairKey) {
    }

    @GetMapping("/activities")
    public List<AdminActivity> activities() {
        return activityRepo.findAll().stream()
                .sorted((a, b) -> {
                    int c = a.getSubject().getName().compareToIgnoreCase(b.getSubject().getName());
                    return c != 0 ? c : Long.compare(a.getId(), b.getId());
                })
                .map(this::toView)
                .toList();
    }

    private AdminActivity toView(ScheduledActivity a) {
        List<Long> groupIds = new ArrayList<>();
        List<String> groupNames = new ArrayList<>();
        a.getStudentGroups().stream()
                .sorted((x, y) -> x.getName().compareToIgnoreCase(y.getName()))
                .forEach(g -> {
                    groupIds.add(g.getId());
                    groupNames.add(g.getName());
                });
        return new AdminActivity(
                a.getId(),
                a.getSubject().getId(), a.getSubject().getCode(), a.getSubject().getName(),
                a.getProfessor() == null ? null : a.getProfessor().getId(),
                a.getProfessor() == null ? null : a.getProfessor().getName(),
                a.getActivityType().name(),
                a.getWeekParity().name(),
                a.getSpecialCategory().name(),
                a.isRequiresAmphitheater(),
                a.isRequiresLab(),
                a.isOnline(),
                a.isPinned(),
                a.getRawType(),
                a.getDurationInSlots(),
                groupIds, groupNames, a.totalStudentCount(),
                a.getRoom() == null ? null : a.getRoom().getName(),
                a.getTimeSlot() == null ? null : a.getTimeSlot().getDayOfWeek().name(),
                a.getTimeSlot() == null ? null : a.getTimeSlot().getSlotIndex(),
                a.getParityPairKey());
    }

    public record ActivityReq(Long subjectId, Long professorId, String activityType, String weekParity,
                              String specialCategory, Boolean requiresAmphitheater, Boolean requiresLab,
                              Boolean online, String rawType, Integer durationInSlots,
                              List<Long> groupIds, String parityPairKey) {
    }

    @PostMapping("/activities")
    @Transactional
    public AdminActivity createActivity(@RequestBody ActivityReq req) {
        return toView(saveActivity(new ScheduledActivity(), req));
    }

    @PutMapping("/activities/{id}")
    @Transactional
    public AdminActivity updateActivity(@PathVariable Long id, @RequestBody ActivityReq req) {
        return toView(saveActivity(activityRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Activitatea nu există (id " + id + ")")), req));
    }

    private ScheduledActivity saveActivity(ScheduledActivity a, ActivityReq req) {
        if (req.subjectId() == null) {
            throw new ConflictException("Disciplina este obligatorie pentru o activitate.");
        }
        a.setSubject(subjectRepo.findById(req.subjectId())
                .orElseThrow(() -> new ConflictException("Disciplina nu există (id " + req.subjectId() + ")")));
        a.setProfessor(req.professorId() == null ? null : professorRepo.findById(req.professorId())
                .orElseThrow(() -> new ConflictException("Profesorul nu există (id " + req.professorId() + ")")));
        a.setActivityType(enumOr(ActivityType.class, req.activityType(), ActivityType.SEMINAR));
        a.setWeekParity(enumOr(WeekParity.class, req.weekParity(), WeekParity.EVERY_WEEK));
        a.setSpecialCategory(enumOr(SpecialCategory.class, req.specialCategory(), SpecialCategory.NORMAL));
        a.setRequiresAmphitheater(Boolean.TRUE.equals(req.requiresAmphitheater()));
        a.setRequiresLab(Boolean.TRUE.equals(req.requiresLab()));
        a.setOnline(Boolean.TRUE.equals(req.online()));
        if (a.isOnline()) {
            // the hour is held nowhere, so it must give back whatever room it held until now
            a.setRoom(null);
        }
        a.setRawType(trimmed(req.rawType()));
        // Carried through so editing one half of an alternating hour does not unpair it.
        a.setParityPairKey(trimmed(req.parityPairKey()));
        a.setDurationInSlots(req.durationInSlots() == null || req.durationInSlots() < 1
                ? 1 : req.durationInSlots());

        Set<StudentGroup> groups = new LinkedHashSet<>();
        if (req.groupIds() != null) {
            for (Long gid : req.groupIds()) {
                groups.add(groupRepo.findById(gid)
                        .orElseThrow(() -> new ConflictException("Grupa nu există (id " + gid + ")")));
            }
        }
        a.setStudentGroups(groups);
        return activityRepo.save(a);
    }

    @DeleteMapping("/activities/{id}")
    @Transactional
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        ScheduledActivity a = activityRepo.findById(id)
                .orElseThrow(() -> new ConflictException("Activitatea nu există (id " + id + ")"));
        activityRepo.delete(a);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ bulk delete

    /** How many rows a "delete everything of this kind" call removed. */
    public record BulkDeleteResult(int deleted, String message) {
    }

    /**
     * Wipes every activity. Nothing else points at an activity except the join table, which is
     * removed by the database, so this always succeeds — and it is what unblocks deleting the
     * subjects, groups and professors the activities refer to.
     */
    @DeleteMapping("/activities")
    @Transactional
    public BulkDeleteResult deleteAllActivities() {
        int n = (int) activityRepo.count();
        activityRepo.deleteAllInBatch();
        return new BulkDeleteResult(n, n + (n == 1 ? " activitate ștearsă." : " activități șterse."));
    }

    @DeleteMapping("/subjects")
    @Transactional
    public BulkDeleteResult deleteAllSubjects() {
        requireNoActivities("disciplinele");
        int n = (int) subjectRepo.count();
        subjectRepo.deleteAllInBatch();
        return new BulkDeleteResult(n, n + (n == 1 ? " disciplină ștearsă." : " discipline șterse."));
    }

    /**
     * Groups are also referenced by special block rules, and that reference is not removed by the
     * database, so it is reported instead of being silently dropped — a blocked interval that
     * quietly loses its audience would change what the rule means.
     */
    @DeleteMapping("/groups")
    @Transactional
    public BulkDeleteResult deleteAllGroups() {
        requireNoActivities("grupele");
        long blocks = specialBlockRepo.findAll().stream()
                .filter(r -> r.getStudentGroup() != null)
                .count();
        if (blocks > 0) {
            throw new ConflictException("Există " + blocks + " blocaje speciale legate de o grupă."
                    + " Șterge-le din Constrângeri întâi, ca să nu rămână reguli fără audiență.");
        }
        int n = (int) groupRepo.count();
        groupRepo.deleteAllInBatch();
        return new BulkDeleteResult(n, n + (n == 1 ? " grupă ștearsă." : " grupe șterse."));
    }

    /**
     * Deleting professors also drops their unavailabilities and room restrictions, which the
     * database removes for us; the message says so, because those are rules the user entered by
     * hand and losing them silently would be a surprise.
     */
    @DeleteMapping("/professors")
    @Transactional
    public BulkDeleteResult deleteAllProfessors() {
        requireNoActivities("profesorii");
        int n = (int) professorRepo.count();
        professorRepo.deleteAllInBatch();
        return new BulkDeleteResult(n, n + (n == 1 ? " profesor șters" : " profesori șterși")
                + ", împreună cu indisponibilitățile și restricțiile lor de sală.");
    }

    /** Subjects, groups and professors are all referenced by activities, so those go first. */
    private void requireNoActivities(String what) {
        long n = activityRepo.count();
        if (n > 0) {
            throw new ConflictException("Nu pot șterge " + what + " cât timp există " + n
                    + (n == 1 ? " activitate care le folosește." : " activități care le folosesc.")
                    + " Șterge întâi activitățile.");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String trimmed(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Lenient enum parse so the UI can send lowercase/blank without a 500. */
    private static <E extends Enum<E>> E enumOr(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        for (E c : type.getEnumConstants()) {
            if (c.name().equalsIgnoreCase(raw.trim())) {
                return c;
            }
        }
        return fallback;
    }
}
