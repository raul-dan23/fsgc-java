package ro.uvt.fsgc.orar.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.dto.ActivityView;
import ro.uvt.fsgc.orar.repository.ProfessorRepository;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.StudentGroupRepository;
import ro.uvt.fsgc.orar.repository.SubjectRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;
import ro.uvt.fsgc.orar.service.ScheduleService;

/** Read access to base data + the current schedule, plus manual drag-and-drop moves. */
@RestController
@RequestMapping("/api/data")
@CrossOrigin
public class DataController {

    private final RoomRepository roomRepo;
    private final ProfessorRepository professorRepo;
    private final StudentGroupRepository groupRepo;
    private final SubjectRepository subjectRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final ScheduleService scheduleService;

    public DataController(RoomRepository roomRepo, ProfessorRepository professorRepo,
                         StudentGroupRepository groupRepo, SubjectRepository subjectRepo,
                         TimeSlotRepository timeSlotRepo, ScheduleService scheduleService) {
        this.roomRepo = roomRepo;
        this.professorRepo = professorRepo;
        this.groupRepo = groupRepo;
        this.subjectRepo = subjectRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.scheduleService = scheduleService;
    }

    @GetMapping("/rooms")
    public List<Room> rooms() {
        return roomRepo.findAll();
    }

    @GetMapping("/professors")
    public List<Professor> professors() {
        return professorRepo.findAll();
    }

    @GetMapping("/groups")
    public List<StudentGroup> groups() {
        return groupRepo.findAll();
    }

    @GetMapping("/subjects")
    public List<Subject> subjects() {
        return subjectRepo.findAll();
    }

    @GetMapping("/timeslots")
    public List<TimeSlot> timeSlots() {
        return timeSlotRepo.findAllByOrderByDayOfWeekAscSlotIndexAsc();
    }

    /** The currently persisted timetable (after the last generate or manual edits). */
    @GetMapping("/schedule")
    public List<ActivityView> schedule() {
        return scheduleService.currentSchedule();
    }

    /** Manual move: assign/clear an activity's slot+room; returns the new view + hard violations. */
    @PutMapping("/activities/{id}/assignment")
    public ResponseEntity<ScheduleService.MoveResult> move(@PathVariable Long id,
                                                           @RequestBody Map<String, Long> body) {
        Long timeSlotId = body.get("timeSlotId");
        Long roomId = body.get("roomId");
        return ResponseEntity.ok(scheduleService.move(id, timeSlotId, roomId));
    }

    /**
     * Empties the grid: every hour goes back to "unplaced", the rules and everything else stay.
     * The caller is expected to have snapshotted first — the UI does, so nothing is ever lost.
     */
    @PostMapping("/schedule/clear")
    public ScheduleService.ClearResult clearSchedule() {
        return scheduleService.clearSchedule();
    }

    /** The rooms this hour could use at that time, and why the others could not. */
    @GetMapping("/activities/{id}/rooms")
    public List<ScheduleService.RoomOption> roomOptions(@PathVariable Long id,
                                                        @RequestParam(required = false) Long timeSlotId) {
        return scheduleService.roomOptions(id, timeSlotId);
    }

    /** Pin an hour where it stands (or release it) so the next generation builds around it. */
    @PutMapping("/activities/{id}/pin")
    public ResponseEntity<ScheduleService.MoveResult> pin(@PathVariable Long id,
                                                          @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(scheduleService.setPinned(id, Boolean.TRUE.equals(body.get("pinned"))));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> onIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }
}
