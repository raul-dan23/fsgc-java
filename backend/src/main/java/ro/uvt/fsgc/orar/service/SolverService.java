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
import java.util.concurrent.atomic.AtomicReference;
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
    /** The solve currently running, if any. Guards against several solves racing to persist. */
    private final AtomicReference<String> activeJob = new AtomicReference<>();

    public SolverService(TimetableDataService data) {
        this.data = data;
    }

    // ------------------------------------------------------------- async generate

    /** A started (or already-running) solve. */
    public record GenerateStart(String jobId, boolean alreadyRunning) {
    }

    /**
     * Starts a solve, unless one is already running. Two concurrent solves would both write the
     * whole timetable back at the end, so the later finisher silently overwrote the other — and
     * the UI could end up showing a result that no longer matched the database.
     */
    public GenerateStart startGenerate(Integer terminationSeconds) {
        String running = activeJob.get();
        if (running != null) {
            TimetableResultDto existing = jobs.get(running);
            if (existing != null && "SOLVING".equals(existing.getState())) {
                return new GenerateStart(running, true);
            }
            activeJob.compareAndSet(running, null);
        }

        int seconds = normalize(terminationSeconds);
        String jobId = UUID.randomUUID().toString();
        TimetableResultDto job = new TimetableResultDto();
        job.setJobId(jobId);
        job.setState("SOLVING");
        jobs.put(jobId, job);
        if (!activeJob.compareAndSet(null, jobId)) {
            // someone won the race in between; hand back their job instead of starting a second
            jobs.remove(jobId);
            String winner = activeJob.get();
            return new GenerateStart(winner, true);
        }

        TimetableSolution problem = data.loadProblem();
        executor.submit(() -> runSolve(jobId, problem, seconds));
        return new GenerateStart(jobId, false);
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
        } finally {
            activeJob.compareAndSet(jobId, null);
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
            String[] text = describe(ca.constraintName(), ca.matchCount());
            conflicts.add(new ConflictItem(ca.constraintName(), text[0], severity,
                    ca.matchCount(), text[1], text[2]));
        }
        return conflicts;
    }

    /**
     * Romanian {name, meaning, advice} for a constraint. The audience is a staff member who has
     * never heard of a solver, so the wording avoids scores, weights and "constraints".
     */
    String[] describe(String constraint, int matches) {
        return switch (constraint) {
            case "No professor overlap" -> new String[] {
                    "Un cadru didactic nu poate fi în două locuri odată",
                    "Același cadru didactic ar preda două ore în același interval.",
                    "Mută una dintre ore în alt interval sau dă-o altui cadru didactic."};
            case "No room overlap" -> new String[] {
                    "O sală nu poate găzdui două ore simultan",
                    "Două activități ar folosi aceeași sală în același interval.",
                    "Eliberează intervalul sau adaugă o sală care poate prelua una dintre ore."};
            case "No student group overlap" -> new String[] {
                    "O grupă nu poate avea două ore odată",
                    "Aceeași grupă ar trebui să fie la două cursuri în același interval.",
                    "Mută una dintre ore; dacă grupa e foarte încărcată, distribuie orele pe mai multe zile."};
            case "Room capacity sufficient" -> new String[] {
                    "Sala trebuie să încapă toți studenții",
                    "Numărul de studenți depășește locurile din sala atribuită.",
                    "Alege o sală mai mare, împarte grupa în două, sau corectează numărul de studenți în Administrare."};
            case "Amphitheater required" -> new String[] {
                    "Cursurile de amfiteatru cer amfiteatru",
                    "O activitate marcată „Curs Amfiteatru” a primit o sală obișnuită.",
                    "Eliberează un amfiteatru în acel interval sau scoate cerința de amfiteatru din Administrare."};
            case "Room availability" -> new String[] {
                    "Sala trebuie să fie disponibilă",
                    "Ora a căzut într-un interval în care sala e marcată indisponibilă.",
                    "Șterge sau restrânge indisponibilitatea sălii din Constrângeri, ori folosește altă sală."};
            case "Master evening only" -> new String[] {
                    "Masteratul se ține doar seara",
                    "O activitate de master a primit un interval din timpul zilei (modulele 1–5).",
                    "Fă loc în modulele 6–8 sau verifică dacă grupa e într-adevăr de master."};
            case "Blocked day for terminal year" -> new String[] {
                    "Ziua blocată rămâne liberă",
                    "S-a programat o oră într-o zi blocată pentru acel an de studiu.",
                    "Scoate regula din Constrângeri dacă ziua nu mai trebuie ținută liberă."};
            case "Special category block" -> new String[] {
                    "Intervalele rezervate (DCT, DPPD, CCOC, limbi) rămân libere",
                    "O oră obișnuită a fost pusă peste un interval rezervat.",
                    "Mută ora sau elimină blocajul din Constrângeri dacă nu mai e valabil."};
            case "Professor unavailability" -> new String[] {
                    "Cadrele didactice nu se programează când sunt indisponibile",
                    "Ora cade peste un interval declarat indisponibil pentru acel cadru didactic.",
                    "Restrânge indisponibilitatea din Constrângeri sau mută ora."};
            case "Professor forbidden room" -> new String[] {
                    "Sălile interzise unui cadru didactic",
                    "Ora a primit o sală marcată ca interzisă pentru acel cadru didactic.",
                    "Alege altă sală sau ridică restricția din Constrângeri."};
            case "Professor only-this room" -> new String[] {
                    "Cadre didactice legate de o singură sală",
                    "Cadrul didactic poate preda doar într-o anumită sală, iar aceasta nu era liberă.",
                    "Eliberează sala respectivă sau ridică restricția din Constrângeri."};
            case "Consecutive slots same building" -> new String[] {
                    "Ore consecutive în aceeași clădire",
                    "O grupă ar trebui să schimbe clădirea între două ore lipite.",
                    "Grupează orele aceleiași grupe în aceeași clădire, dacă se poate."};
            case "Unassigned activity" -> new String[] {
                    "Ore rămase neprogramate",
                    matches + (matches == 1 ? " oră nu a încăput" : " ore nu au încăput")
                            + " nicăieri fără să încalce o regulă.",
                    "Vezi lista detaliată de mai sus — pentru fiecare oră scrie ce anume o blochează."};
            case "Alternating halves share slot and room" -> new String[] {
                    "Ora alternativă (SI/SP) stă într-o singură celulă",
                    "Cele două jumătăți ale unei ore care alternează săptămânal"
                            + " au ajuns în intervale sau săli diferite.",
                    "Nu blochează orarul, dar se citește mai greu. Scade ponderea"
                            + " „Ore alternative SI/SP în același interval” din Constrângeri"
                            + " dacă preferi libertate mai mare la plasare."};
            default -> new String[] {
                    constraint,
                    "Regula „" + constraint + "” nu a putut fi respectată complet.",
                    "Verifică datele legate de această regulă în Administrare sau Constrângeri."};
        };
    }

    private ActivityView toView(ScheduledActivity a) {
        return ActivityMapper.toView(a);
    }

    private static String fmt(LocalTime t) {
        return t == null ? null : t.format(HM);
    }
}
