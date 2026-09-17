package ro.uvt.fsgc.orar.controller;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uvt.fsgc.orar.domain.BlockedDayRule;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;
import ro.uvt.fsgc.orar.domain.ProfessorUnavailability;
import ro.uvt.fsgc.orar.domain.RestrictionType;
import ro.uvt.fsgc.orar.domain.RoomAvailability;
import ro.uvt.fsgc.orar.domain.RoomUnavailability;
import ro.uvt.fsgc.orar.domain.SpecialBlockRule;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.repository.BlockedDayRuleRepository;
import ro.uvt.fsgc.orar.repository.ProfessorRepository;
import ro.uvt.fsgc.orar.repository.ProfessorRoomRestrictionRepository;
import ro.uvt.fsgc.orar.repository.ProfessorUnavailabilityRepository;
import ro.uvt.fsgc.orar.repository.RoomAvailabilityRepository;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.RoomUnavailabilityRepository;
import ro.uvt.fsgc.orar.repository.SpecialBlockRuleRepository;
import ro.uvt.fsgc.orar.repository.StudentGroupRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

/**
 * CRUD for the constraint rules that are NOT in the Excel template and change rarely: blocked days,
 * special-interval blocks, professor unavailabilities, professor-room restrictions, and per-day room
 * availability. Requests use simple DTOs with ids; references are resolved to managed entities.
 */
@RestController
@RequestMapping("/api/rules")
@CrossOrigin
public class RulesController {

    private final BlockedDayRuleRepository blockedDayRepo;
    private final SpecialBlockRuleRepository specialBlockRepo;
    private final ProfessorUnavailabilityRepository profUnavailRepo;
    private final ProfessorRoomRestrictionRepository profRoomRepo;
    private final RoomAvailabilityRepository roomAvailRepo;
    private final RoomUnavailabilityRepository roomUnavailRepo;
    private final ProfessorRepository professorRepo;
    private final RoomRepository roomRepo;
    private final StudentGroupRepository groupRepo;
    private final TimeSlotRepository timeSlotRepo;

    public RulesController(BlockedDayRuleRepository blockedDayRepo, SpecialBlockRuleRepository specialBlockRepo,
                          ProfessorUnavailabilityRepository profUnavailRepo,
                          ProfessorRoomRestrictionRepository profRoomRepo,
                          RoomAvailabilityRepository roomAvailRepo,
                          RoomUnavailabilityRepository roomUnavailRepo, ProfessorRepository professorRepo,
                          RoomRepository roomRepo, StudentGroupRepository groupRepo,
                          TimeSlotRepository timeSlotRepo) {
        this.blockedDayRepo = blockedDayRepo;
        this.specialBlockRepo = specialBlockRepo;
        this.profUnavailRepo = profUnavailRepo;
        this.profRoomRepo = profRoomRepo;
        this.roomAvailRepo = roomAvailRepo;
        this.roomUnavailRepo = roomUnavailRepo;
        this.professorRepo = professorRepo;
        this.roomRepo = roomRepo;
        this.groupRepo = groupRepo;
        this.timeSlotRepo = timeSlotRepo;
    }

    // ---------------- blocked days ----------------

    public record BlockedDayReq(StudyProgram studyProgram, Integer year, DayOfWeek dayOfWeek,
                                String semester, String academicYear) {
    }

    @GetMapping("/blocked-days")
    public List<BlockedDayRule> blockedDays() {
        return blockedDayRepo.findAll();
    }

    @PostMapping("/blocked-days")
    public BlockedDayRule addBlockedDay(@RequestBody BlockedDayReq req) {
        BlockedDayRule r = new BlockedDayRule();
        r.setStudyProgram(req.studyProgram());
        r.setYear(req.year());
        r.setDayOfWeek(req.dayOfWeek());
        r.setSemester(req.semester());
        r.setAcademicYear(req.academicYear());
        return blockedDayRepo.save(r);
    }

    @DeleteMapping("/blocked-days/{id}")
    public ResponseEntity<Void> deleteBlockedDay(@PathVariable Long id) {
        blockedDayRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- special interval blocks ----------------

    public record SpecialBlockReq(Long studentGroupId, String specialization, Integer year,
                                  DayOfWeek dayOfWeek, Long timeSlotId, SpecialCategory category,
                                  String semester, String academicYear) {
    }

    @GetMapping("/special-blocks")
    public List<SpecialBlockRule> specialBlocks() {
        return specialBlockRepo.findAll();
    }

    @PostMapping("/special-blocks")
    public SpecialBlockRule addSpecialBlock(@RequestBody SpecialBlockReq req) {
        SpecialBlockRule r = new SpecialBlockRule();
        if (req.studentGroupId() != null) {
            r.setStudentGroup(groupRepo.findById(req.studentGroupId()).orElseThrow());
        }
        r.setSpecialization(req.specialization());
        r.setYear(req.year());
        r.setDayOfWeek(req.dayOfWeek());
        r.setTimeSlot(timeSlotRepo.findById(req.timeSlotId()).orElseThrow());
        r.setCategory(req.category());
        r.setSemester(req.semester());
        r.setAcademicYear(req.academicYear());
        return specialBlockRepo.save(r);
    }

    @DeleteMapping("/special-blocks/{id}")
    public ResponseEntity<Void> deleteSpecialBlock(@PathVariable Long id) {
        specialBlockRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- professor unavailability ----------------

    public record ProfUnavailReq(Long professorId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    @GetMapping("/professor-unavailabilities")
    public List<ProfessorUnavailability> profUnavailabilities() {
        return profUnavailRepo.findAll();
    }

    @PostMapping("/professor-unavailabilities")
    public ProfessorUnavailability addProfUnavail(@RequestBody ProfUnavailReq req) {
        ProfessorUnavailability u = new ProfessorUnavailability();
        u.setProfessor(professorRepo.findById(req.professorId()).orElseThrow());
        u.setDayOfWeek(req.dayOfWeek());
        u.setStartTime(req.startTime());
        u.setEndTime(req.endTime());
        return profUnavailRepo.save(u);
    }

    @DeleteMapping("/professor-unavailabilities/{id}")
    public ResponseEntity<Void> deleteProfUnavail(@PathVariable Long id) {
        profUnavailRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- professor-room restriction ----------------

    public record ProfRoomReq(Long professorId, Long roomId, RestrictionType restrictionType) {
    }

    @GetMapping("/professor-room-restrictions")
    public List<ProfessorRoomRestriction> profRoomRestrictions() {
        return profRoomRepo.findAll();
    }

    @PostMapping("/professor-room-restrictions")
    public ProfessorRoomRestriction addProfRoom(@RequestBody ProfRoomReq req) {
        ProfessorRoomRestriction r = new ProfessorRoomRestriction();
        r.setProfessor(professorRepo.findById(req.professorId()).orElseThrow());
        r.setRoom(roomRepo.findById(req.roomId()).orElseThrow());
        r.setRestrictionType(req.restrictionType());
        return profRoomRepo.save(r);
    }

    @DeleteMapping("/professor-room-restrictions/{id}")
    public ResponseEntity<Void> deleteProfRoom(@PathVariable Long id) {
        profRoomRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- room availability ----------------

    public record RoomAvailReq(Long roomId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    @GetMapping("/room-availabilities")
    public List<RoomAvailability> roomAvailabilities() {
        return roomAvailRepo.findAll();
    }

    @PostMapping("/room-availabilities")
    public RoomAvailability addRoomAvail(@RequestBody RoomAvailReq req) {
        RoomAvailability a = new RoomAvailability();
        a.setRoom(roomRepo.findById(req.roomId()).orElseThrow());
        a.setDayOfWeek(req.dayOfWeek());
        a.setStartTime(req.startTime());
        a.setEndTime(req.endTime());
        return roomAvailRepo.save(a);
    }

    @DeleteMapping("/room-availabilities/{id}")
    public ResponseEntity<Void> deleteRoomAvail(@PathVariable Long id) {
        roomAvailRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------- room unavailability ----------------

    /** Carries the room name, so the UI never has to render a bare id. */
    public record RoomUnavailView(Long id, Long roomId, String roomName, String dayOfWeek,
                                  String startTime, String endTime, String reason) {
    }

    private static RoomUnavailView view(RoomUnavailability u) {
        return new RoomUnavailView(u.getId(), u.getRoom().getId(), u.getRoom().getName(),
                u.getDayOfWeek().name(), u.getStartTime().toString(), u.getEndTime().toString(),
                u.getReason());
    }

    public record RoomUnavailReq(Long roomId, DayOfWeek dayOfWeek, LocalTime startTime,
                                 LocalTime endTime, String reason) {
    }

    @GetMapping("/room-unavailabilities")
    public List<RoomUnavailView> roomUnavailabilities() {
        return roomUnavailRepo.findAll().stream()
                .sorted((a, b) -> {
                    int c = a.getRoom().getName().compareToIgnoreCase(b.getRoom().getName());
                    if (c != 0) {
                        return c;
                    }
                    c = a.getDayOfWeek().compareTo(b.getDayOfWeek());
                    return c != 0 ? c : a.getStartTime().compareTo(b.getStartTime());
                })
                .map(RulesController::view)
                .toList();
    }

    @PostMapping("/room-unavailabilities")
    public ResponseEntity<?> addRoomUnavail(@RequestBody RoomUnavailReq req) {
        if (req.roomId() == null || req.dayOfWeek() == null
                || req.startTime() == null || req.endTime() == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message",
                    "Sala, ziua, ora de început și ora de sfârșit sunt obligatorii."));
        }
        if (!req.startTime().isBefore(req.endTime())) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message",
                    "Ora de început trebuie să fie înaintea orei de sfârșit."));
        }
        RoomUnavailability u = new RoomUnavailability();
        u.setRoom(roomRepo.findById(req.roomId()).orElseThrow());
        u.setDayOfWeek(req.dayOfWeek());
        u.setStartTime(req.startTime());
        u.setEndTime(req.endTime());
        u.setReason(req.reason() == null || req.reason().isBlank() ? null : req.reason().trim());
        return ResponseEntity.ok(view(roomUnavailRepo.save(u)));
    }

    @DeleteMapping("/room-unavailabilities/{id}")
    public ResponseEntity<Void> deleteRoomUnavail(@PathVariable Long id) {
        roomUnavailRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
