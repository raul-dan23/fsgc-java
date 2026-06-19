package ro.uvt.fsgc.orar.service;

import ai.timefold.solver.core.api.score.analysis.ConstraintAnalysis;
import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.dto.ActivityView;
import ro.uvt.fsgc.orar.dto.CompareResultDto;
import ro.uvt.fsgc.orar.dto.ConflictItem;
import ro.uvt.fsgc.orar.dto.TimetableResultDto;
import ro.uvt.fsgc.orar.solver.TimetableConstraintProvider;
import ro.uvt.fsgc.orar.solver.TimetableSolution;

/**
 * Orchestrates Timefold solving: builds a per-request {@link SolverConfig} so the time budget is
 * dynamic, runs jobs asynchronously, analyzes the score for a conflict report, and offers a
 * "compare budgets" mode. Problem loading/persistence is delegated to {@link TimetableDataService}.
 */
@Service
public class SolverService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DEFAULT_SECONDS = 60;

    private final TimetableDataService data;

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    private final Map<String, TimetableResultDto> jobs = new ConcurrentHashMap<>();

    public SolverService(TimetableDataService data) {
        this.data = data;
    }

    // ------------------------------------------------------------- async generate

    public String startGenerate(Integer terminationSeconds) {
        int seconds = normalize(terminationSeconds);
        String jobId = UUID.randomUUID().toString();
        TimetableResultDto job = new TimetableResultDto();
        job.setJobId(jobId);
        job.setState("SOLVING");
        jobs.put(jobId, job);

        TimetableSolution problem = data.loadProblem();
        executor.submit(() -> runSolve(jobId, problem, seconds));
        return jobId;
    }

    public TimetableResultDto getJob(String jobId) {
        return jobs.get(jobId);
    }

    private void runSolve(String jobId, TimetableSolution problem, int seconds) {
        TimetableResultDto job = jobs.get(jobId);
        try {
            SolverFactory<TimetableSolution> factory = SolverFactory.create(buildConfig(seconds));
            Solver<TimetableSolution> solver = factory.buildSolver();
            long start = System.currentTimeMillis();
            TimetableSolution solved = solver.solve(problem);
            long millis = System.currentTimeMillis() - start;

            SolutionManager<TimetableSolution, HardMediumSoftScore> sm = SolutionManager.create(factory);
            ScoreAnalysis<HardMediumSoftScore> analysis = sm.analyze(solved);

            fillResult(job, solved, millis, analysis);
            data.persistSolution(solved);
            job.setState("COMPLETED");
        } catch (Exception e) {
            job.setState("FAILED");
            job.setError(e.getMessage());
        }
    }

    // ------------------------------------------------------------- compare budgets

    public List<CompareResultDto> compare(List<Integer> budgets) {
        List<CompareResultDto> rows = new ArrayList<>();
        for (Integer b : budgets) {
            int seconds = normalize(b);
            TimetableSolution problem = data.loadProblem();
            SolverFactory<TimetableSolution> factory = SolverFactory.create(buildConfig(seconds));
            long start = System.currentTimeMillis();
            TimetableSolution solved = factory.buildSolver().solve(problem);
            long millis = System.currentTimeMillis() - start;

            HardMediumSoftScore score = solved.getScore();
            SolutionManager<TimetableSolution, HardMediumSoftScore> sm = SolutionManager.create(factory);
            int violatedHard = (int) sm.analyze(solved).constraintAnalyses().stream()
                    .filter(ca -> ca.score().hardScore() < 0).count();
            int unassigned = (int) solved.getActivities().stream()
                    .filter(a -> a.getTimeSlot() == null || a.getRoom() == null).count();
            rows.add(new CompareResultDto(seconds, millis, score.toString(),
                    score.hardScore(), score.mediumScore(), score.softScore(), unassigned, violatedHard));
        }
        return rows;
    }

    // ------------------------------------------------------------- helpers

    private static int normalize(Integer seconds) {
        return seconds == null || seconds <= 0 ? DEFAULT_SECONDS : seconds;
    }

    private SolverConfig buildConfig(int seconds) {
        return new SolverConfig()
                .withSolutionClass(TimetableSolution.class)
                .withEntityClasses(ScheduledActivity.class)
                .withConstraintProviderClass(TimetableConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit((long) seconds));
    }

    private void fillResult(TimetableResultDto job, TimetableSolution solved, long millis,
                            ScoreAnalysis<HardMediumSoftScore> analysis) {
        HardMediumSoftScore score = solved.getScore();
        job.setScore(score.toString());
        job.setHardScore(score.hardScore());
        job.setMediumScore(score.mediumScore());
        job.setSoftScore(score.softScore());
        job.setSolveMillis(millis);
        job.setTotalActivities(solved.getActivities().size());

        int unassigned = (int) solved.getActivities().stream()
                .filter(a -> a.getTimeSlot() == null || a.getRoom() == null).count();
        job.setUnassignedCount(unassigned);

        job.setActivities(solved.getActivities().stream().map(this::toView).collect(Collectors.toList()));
        job.setConflicts(buildConflicts(analysis));
    }

    private List<ConflictItem> buildConflicts(ScoreAnalysis<HardMediumSoftScore> analysis) {
        List<ConflictItem> conflicts = new ArrayList<>();
        for (ConstraintAnalysis<HardMediumSoftScore> ca : analysis.constraintAnalyses()) {
            HardMediumSoftScore s = ca.score();
            if (s.hardScore() == 0 && s.mediumScore() == 0) {
                continue; // only report things that hurt feasibility / placement
            }
            String severity = s.hardScore() < 0 ? "HARD" : "PLACEMENT";
            conflicts.add(new ConflictItem(ca.constraintName(), severity, ca.matchCount(),
                    suggestionFor(ca.constraintName(), ca.matchCount())));
        }
        return conflicts;
    }

    private String suggestionFor(String constraint, int matches) {
        return switch (constraint) {
            case "Room capacity sufficient" ->
                    "Some groups exceed every available room. Add a bigger room/amphitheater or split the group.";
            case "Amphitheater required" ->
                    "A 'Curs Amfiteatru' has no amphitheater free in its slot. Add amphitheater availability.";
            case "Room availability" ->
                    "An activity has no room available in its slot. Widen room availability windows.";
            case "No room overlap", "No professor overlap", "No student group overlap" ->
                    "Too many activities compete for the same slot. Free up slots or add resources.";
            case "Master evening only" ->
                    "Master activities only fit modules 6-8. Ensure enough evening room availability.";
            case "Professor unavailability", "Professor forbidden room", "Professor only-this room" ->
                    "A professor restriction cannot be satisfied. Relax the restriction or add eligible rooms.";
            case "Unassigned activity" ->
                    matches + " activities could not be placed without breaking a hard rule.";
            default -> "Constraint '" + constraint + "' is violated " + matches + " time(s).";
        };
    }

    private ActivityView toView(ScheduledActivity a) {
        return ActivityMapper.toView(a);
    }

    private static String fmt(LocalTime t) {
        return t == null ? null : t.format(HM);
    }
}
