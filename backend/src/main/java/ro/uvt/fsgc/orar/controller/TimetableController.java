package ro.uvt.fsgc.orar.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uvt.fsgc.orar.dto.CompareRequest;
import ro.uvt.fsgc.orar.dto.CompareResultDto;
import ro.uvt.fsgc.orar.dto.GenerateRequest;
import ro.uvt.fsgc.orar.dto.TimetableResultDto;
import ro.uvt.fsgc.orar.service.SolverService;

@RestController
@RequestMapping("/api/timetable")
@CrossOrigin
public class TimetableController {

    private final SolverService solverService;

    public TimetableController(SolverService solverService) {
        this.solverService = solverService;
    }

    /** Starts an asynchronous solve and returns the job id. */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generate(@RequestBody(required = false) GenerateRequest request) {
        Integer seconds = request == null ? null : request.getTerminationSeconds();
        String jobId = solverService.startGenerate(seconds);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "state", "SOLVING"));
    }

    /** Lightweight status (state + score + counts) for polling. */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String jobId) {
        TimetableResultDto job = solverService.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "jobId", job.getJobId(),
                "state", job.getState(),
                "score", job.getScore() == null ? "" : job.getScore(),
                "unassigned", job.getUnassignedCount(),
                "total", job.getTotalActivities()));
    }

    /** Full result: score breakdown, conflicts, and the placed activities. */
    @GetMapping("/result/{jobId}")
    public ResponseEntity<TimetableResultDto> result(@PathVariable String jobId) {
        TimetableResultDto job = solverService.getJob(jobId);
        return job == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(job);
    }

    /** Runs several time budgets successively on the same input and returns a quality table. */
    @PostMapping("/compare")
    public ResponseEntity<List<CompareResultDto>> compare(@RequestBody CompareRequest request) {
        if (request.getBudgets() == null || request.getBudgets().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(solverService.compare(request.getBudgets()));
    }
}
