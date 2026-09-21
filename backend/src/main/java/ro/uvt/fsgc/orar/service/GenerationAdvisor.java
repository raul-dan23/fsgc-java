package ro.uvt.fsgc.orar.service;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.uvt.fsgc.orar.domain.BlockedDayRule;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;
import ro.uvt.fsgc.orar.domain.ProfessorUnavailability;
import ro.uvt.fsgc.orar.domain.RestrictionType;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.SpecialBlockRule;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.dto.BudgetAdvice;
import ro.uvt.fsgc.orar.dto.UnassignedDiagnostic;
import ro.uvt.fsgc.orar.repository.BlockedDayRuleRepository;
import ro.uvt.fsgc.orar.repository.ProfessorRoomRestrictionRepository;
import ro.uvt.fsgc.orar.repository.ProfessorUnavailabilityRepository;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.SpecialBlockRuleRepository;
import ro.uvt.fsgc.orar.solver.TimetableConstraintProvider;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

/**
 * Advice around a solve: how long to run it, and — afterwards — why individual activities could
 * not be placed. The diagnosis re-checks the same hard rules the solver uses, one candidate
 * slot+room at a time, so it can report both the reason and the closest near-misses.
 */
@Service
public class GenerationAdvisor {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    /** Budgets offered to the user; the recommendation is rounded up to one of these. */
    /** Monday to Friday: the timetable has no weekend. */
    private static final int WEEKDAYS = 5;

    private static final int[] LADDER = {30, 60, 90, 120, 180, 300, 600, 900};

    private final ScheduledActivityRepository activityRepo;
    private final RoomRepository roomRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final BlockedDayRuleRepository blockedDayRepo;
    private final SpecialBlockRuleRepository specialBlockRepo;
    private final ProfessorUnavailabilityRepository profUnavailRepo;
    private final ProfessorRoomRestrictionRepository profRoomRepo;

    public GenerationAdvisor(ScheduledActivityRepository activityRepo, RoomRepository roomRepo,
                             TimeSlotRepository timeSlotRepo, BlockedDayRuleRepository blockedDayRepo,
                             SpecialBlockRuleRepository specialBlockRepo,
                             ProfessorUnavailabilityRepository profUnavailRepo,
                             ProfessorRoomRestrictionRepository profRoomRepo) {
        this.activityRepo = activityRepo;
        this.roomRepo = roomRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.blockedDayRepo = blockedDayRepo;
        this.specialBlockRepo = specialBlockRepo;
        this.profUnavailRepo = profUnavailRepo;
        this.profRoomRepo = profRoomRepo;
    }

    // ============================================================ budget advice

    /**
     * Suggests a time budget from how tight the problem is. The dominant pressure — room-slots,
     * the busiest group, the busiest professor, or amphitheater demand — drives the multiplier,
     * because that is what the solver will struggle against.
     */
    @Transactional(readOnly = true)
    public BudgetAdvice suggestBudget() {
        List<ScheduledActivity> activities = activityRepo.findAll();
        List<Room> rooms = roomRepo.findAll();
        List<TimeSlot> slots = timeSlotRepo.findAll();

        int nA = activities.size();
        int nR = rooms.size();
        int nS = slots.size();

        if (nA == 0 || nR == 0 || nS == 0) {
            return new BudgetAdvice(30, 60, 120,
                    "Nu există date suficiente pentru o recomandare.",
                    List.of("Importă datele semestrului înainte de generare."),
                    List.of(), nA, nR, nS, 0);
        }

        int roomSlots = nR * nS;
        // Online hours take an hour but no room, so they weigh on the groups and the professors,
        // never on the rooms: counting them here would overstate how tight the rooms are.
        int onSite = (int) activities.stream().filter(a -> !a.isOnline()).count();
        double occupancy = (double) onSite / roomSlots;

        Map<Long, Integer> perGroup = new HashMap<>();
        Map<Long, Integer> perProfessor = new HashMap<>();
        int amphiDemand = 0;
        int masterCount = 0;
        for (ScheduledActivity a : activities) {
            for (StudentGroup g : a.getStudentGroups()) {
                perGroup.merge(g.getId(), 1, Integer::sum);
            }
            if (a.getProfessor() != null) {
                perProfessor.merge(a.getProfessor().getId(), 1, Integer::sum);
            }
            if (a.isRequiresAmphitheater() && !a.isOnline()) {
                amphiDemand++;
            }
            if (a.isMaster()) {
                masterCount++;
            }
        }
        int maxGroupLoad = perGroup.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int maxProfLoad = perProfessor.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int amphiRooms = (int) rooms.stream().filter(r -> r.getTypology() == RoomTypology.AMPHITHEATER).count();
        int eveningSlots = (int) slots.stream().filter(TimeSlot::isEveningModule).count();

        // A group cannot use every module of the week: at most five a day, five days.
        int groupCeiling = Math.min(nS, TimetableConstraintProvider.MAX_MODULES_A_DAY * WEEKDAYS);
        double groupPressure = (double) maxGroupLoad / groupCeiling;
        double profPressure = (double) maxProfLoad / nS;
        double amphiPressure = amphiRooms == 0 ? (amphiDemand > 0 ? 2.0 : 0)
                : (double) amphiDemand / (amphiRooms * nS);
        double masterPressure = eveningSlots == 0 ? (masterCount > 0 ? 2.0 : 0)
                : (double) masterCount / (nR * (double) eveningSlots);

        double tightness = Math.max(Math.max(occupancy, groupPressure),
                Math.max(profPressure, Math.max(amphiPressure, masterPressure)));

        double base = 20 + nA / 5.0;
        double factor = tightness < 0.30 ? 1.0
                : tightness < 0.50 ? 1.6
                : tightness < 0.70 ? 2.6
                : 4.0;
        int recommended = ladder((int) Math.round(base * factor));
        int quick = ladder(Math.max(30, recommended / 2));
        int thorough = ladder(recommended * 3);

        List<String> reasons = new ArrayList<>();
        reasons.add(nA == onSite
                ? String.format("%d activități pe %d săli × %d module = %d combinații (ocupare %.0f%%).",
                        nA, nR, nS, roomSlots, occupancy * 100)
                : String.format("%d activități, din care %d în sală, pe %d săli × %d module = %d"
                                + " combinații (ocupare %.0f%%). Cele %d ore online nu ocupă sală.",
                        nA, onSite, nR, nS, roomSlots, occupancy * 100, nA - onSite));
        reasons.add(String.format(
                "Grupa cea mai încărcată are %d activități din %d module posibile pe săptămână"
                        + " (%.0f%%), la cel mult %d module pe zi.",
                maxGroupLoad, groupCeiling, groupPressure * 100,
                TimetableConstraintProvider.MAX_MODULES_A_DAY));
        reasons.add(String.format(
                "Cadrul didactic cel mai încărcat are %d activități din %d module (%.0f%%).",
                maxProfLoad, nS, profPressure * 100));
        if (amphiDemand > 0) {
            reasons.add(String.format(
                    "%d activități cer amfiteatru, iar există %d amfiteatre (%d locuri în orar).",
                    amphiDemand, amphiRooms, amphiRooms * nS));
        }
        if (masterCount > 0) {
            reasons.add(String.format(
                    "%d activități de master încap doar în cele %d module de seară.",
                    masterCount, eveningSlots));
        }

        long pinned = activities.stream().filter(ScheduledActivity::isPinned).count();
        if (pinned > 0) {
            reasons.add(String.format(
                    "%d %s fixate manual și rămân pe loc; restul orarului se construiește în jurul lor.",
                    pinned, pinned == 1 ? "oră este fixată" : "ore sunt"));
        }

        List<String> blockers = structuralBlockers(activities, rooms, nS, eveningSlots, amphiRooms);
        blockers.addAll(pinnedClashes(activities));

        String summary;
        if (!blockers.isEmpty()) {
            summary = "Câteva ore nu pot fi plasate indiferent de buget — vezi lista de mai jos. "
                    + "Restul orarului se poate genera normal.";
        } else {
            summary = tightness < 0.30
                    ? "Problema este lejeră — un buget mic ar trebui să ajungă pentru 0 ore neplasate."
                    : tightness < 0.50
                    ? "Problemă de dificultate medie. Bugetul recomandat ar trebui să plaseze tot."
                    : tightness < 0.70
                    ? "Problemă strânsă: o resursă e aproape saturată, deci merită timp suplimentar."
                    : "Problemă foarte strânsă. Chiar și cu timp mult pot rămâne ore neplasate.";
        }

        return new BudgetAdvice(quick, recommended, thorough, summary, reasons, blockers,
                nA, nR, nS, (int) Math.round(occupancy * 100));
    }

    /**
     * Pins that contradict each other. The solver cannot move either hour, so it can only report
     * the clash afterwards — better to say it before anyone waits for a generation.
     */
    static List<String> pinnedClashes(List<ScheduledActivity> activities) {
        List<ScheduledActivity> pinned = activities.stream()
                .filter(a -> a.isPinned() && a.isPlaced())
                .toList();
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i < pinned.size(); i++) {
            for (int j = i + 1; j < pinned.size(); j++) {
                ScheduledActivity a = pinned.get(i);
                ScheduledActivity b = pinned.get(j);
                if (!a.getTimeSlot().getId().equals(b.getTimeSlot().getId())
                        || !a.parityClashesWith(b)) {
                    continue;
                }
                String when = " în " + a.getTimeSlot().getDayOfWeek() + ", modulul "
                        + a.getTimeSlot().getSlotIndex() + ".";
                String pair = "„" + a.getSubject().getName() + "” și „" + b.getSubject().getName() + "”";
                if (a.getRoom() != null && a.getRoom().equals(b.getRoom())) {
                    out.add("Două ore fixate cer aceeași sală (" + a.getRoom().getName() + "): "
                            + pair + when);
                }
                if (a.getProfessor() != null && a.getProfessor().equals(b.getProfessor())) {
                    out.add("Două ore fixate cer același cadru didactic ("
                            + a.getProfessor().getName() + "): " + pair + when);
                }
                if (!java.util.Collections.disjoint(a.getStudentGroups(), b.getStudentGroups())) {
                    out.add("Două ore fixate cad peste aceeași grupă: " + pair + when);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** Impossibilities that more solving time cannot fix; each needs a data change instead. */
    static List<String> structuralBlockers(List<ScheduledActivity> activities, List<Room> rooms,
                                           int nS, int eveningSlots, int amphiRooms) {
        // LinkedHashSet: a subject with several activities would otherwise repeat the same line
        java.util.Set<String> blockers = new java.util.LinkedHashSet<>();
        int maxCapacity = rooms.stream().mapToInt(Room::getCapacity).max().orElse(0);
        int maxAmphi = rooms.stream().filter(r -> r.getTypology() == RoomTypology.AMPHITHEATER)
                .mapToInt(Room::getCapacity).max().orElse(0);
        int maxLab = rooms.stream().filter(r -> r.getTypology() == RoomTypology.LAB)
                .mapToInt(Room::getCapacity).max().orElse(0);
        long labRooms = rooms.stream().filter(r -> r.getTypology() == RoomTypology.LAB).count();

        for (ScheduledActivity a : activities) {
            if (a.isOnline()) {
                continue; // holds no room, so no room is too small for it
            }
            int students = a.totalStudentCount();
            if (students > maxCapacity) {
                blockers.add(String.format(
                        "„%s” are %d studenți, dar cea mai mare sală are %d locuri."
                                + " Împarte grupele sau, dacă ora se ține online, bifează"
                                + " „Online” la activitate în Administrare.",
                        a.getSubject().getName(), students, maxCapacity));
            } else if (a.isRequiresAmphitheater() && students > maxAmphi) {
                blockers.add(String.format(
                        "„%s” cere amfiteatru pentru %d studenți, dar cel mai mare amfiteatru are %d locuri.",
                        a.getSubject().getName(), students, maxAmphi));
            } else if (a.isRequiresLab() && students > maxLab) {
                blockers.add(String.format(
                        "„%s” cere laborator pentru %d studenți, dar cel mai mare laborator are %d locuri.",
                        a.getSubject().getName(), students, maxLab));
            }
        }
        if (amphiRooms == 0 && activities.stream().anyMatch(ScheduledActivity::isRequiresAmphitheater)) {
            blockers.add("Există activități care cer amfiteatru, dar nicio sală nu e de tip amfiteatru.");
        }
        if (labRooms == 0 && activities.stream().anyMatch(ScheduledActivity::isRequiresLab)) {
            blockers.add("Există activități care cer laborator, dar nicio sală nu e de tip laborator.");
        }

        Map<Long, Integer> perGroup = new HashMap<>();
        Map<Long, String> groupNames = new HashMap<>();
        Map<Long, Integer> masterPerGroup = new HashMap<>();
        for (ScheduledActivity a : activities) {
            for (StudentGroup g : a.getStudentGroups()) {
                perGroup.merge(g.getId(), 1, Integer::sum);
                groupNames.put(g.getId(), g.getName());
                if (a.isMaster()) {
                    masterPerGroup.merge(g.getId(), 1, Integer::sum);
                }
            }
        }
        int weekCeiling = Math.min(nS, TimetableConstraintProvider.MAX_MODULES_A_DAY * WEEKDAYS);
        perGroup.forEach((id, count) -> {
            if (count > weekCeiling) {
                blockers.add(String.format(
                        "Grupa %s are %d activități, dar o grupă stă cel mult %d module pe zi,"
                                + " adică %d pe săptămână.",
                        groupNames.get(id), count, TimetableConstraintProvider.MAX_MODULES_A_DAY,
                        weekCeiling));
            }
        });
        masterPerGroup.forEach((id, count) -> {
            if (count > eveningSlots) {
                blockers.add(String.format(
                        "Grupa de master %s are %d activități, dar există doar %d module de seară.",
                        groupNames.get(id), count, eveningSlots));
            }
        });
        // keep the report readable when the data is badly broken
        List<String> out = new ArrayList<>(blockers);
        if (out.size() > 12) {
            List<String> trimmed = new ArrayList<>(out.subList(0, 12));
            trimmed.add("… și încă " + (out.size() - 12) + " probleme de același tip.");
            return trimmed;
        }
        return out;
    }

    private static int ladder(int seconds) {
        for (int step : LADDER) {
            if (seconds <= step) {
                return step;
            }
        }
        return LADDER[LADDER.length - 1];
    }

    // ============================================================ unassigned diagnosis

    /**
     * For every activity left without a slot or room, finds what stood in the way. Scans all
     * slot+room pairs, counts the hard rules each would break, and returns the least-bad ones —
     * including options that do break a rule, which is often the practical way out.
     */
    @Transactional(readOnly = true)
    public List<UnassignedDiagnostic> diagnoseUnassigned() {
        List<ScheduledActivity> all = activityRepo.findAll();
        List<ScheduledActivity> unplaced = all.stream()
                .filter(a -> !a.isPlaced())
                .toList();
        if (unplaced.isEmpty()) {
            return List.of();
        }
        List<Room> rooms = roomRepo.findAll();
        List<TimeSlot> slots = timeSlotRepo.findAllByOrderByDayOfWeekAscSlotIndexAsc();
        List<BlockedDayRule> blockedDays = blockedDayRepo.findAll();
        List<SpecialBlockRule> specialBlocks = specialBlockRepo.findAll();
        List<ProfessorUnavailability> profUnavail = profUnavailRepo.findAll();
        List<ProfessorRoomRestriction> profRooms = profRoomRepo.findAll();

        List<UnassignedDiagnostic> out = new ArrayList<>();
        for (ScheduledActivity a : unplaced) {
            out.add(diagnoseOne(a, all, rooms, slots, blockedDays, specialBlocks, profUnavail,
                    profRooms));
        }
        return out;
    }

    private UnassignedDiagnostic diagnoseOne(ScheduledActivity a, List<ScheduledActivity> all,
                                             List<Room> rooms, List<TimeSlot> slots,
                                             List<BlockedDayRule> blockedDays,
                                             List<SpecialBlockRule> specialBlocks,
                                             List<ProfessorUnavailability> profUnavail,
                                             List<ProfessorRoomRestriction> profRooms) {
        int students = a.totalStudentCount();
        List<String> groupNames = a.getStudentGroups().stream()
                .map(StudentGroup::getName).sorted().toList();

        List<UnassignedDiagnostic.PlacementOption> options = new ArrayList<>();
        // An online hour takes no room, so its only choice is the interval.
        List<Room> candidates = a.isOnline() ? Collections.singletonList(null) : rooms;
        for (TimeSlot ts : slots) {
            for (Room room : candidates) {
                List<String> v = violationsFor(a, ts, room, all, blockedDays, specialBlocks,
                        profUnavail, profRooms);
                options.add(new UnassignedDiagnostic.PlacementOption(
                        ts.getDayOfWeek().name(), ts.getSlotIndex(),
                        ts.getStartTime().format(HM) + " – " + ts.getEndTime().format(HM),
                        room == null ? "ONLINE" : room.getName(), v));
            }
        }
        options.sort(Comparator.comparingInt(o -> o.violations().size()));

        int bestCount = options.isEmpty() ? -1 : options.get(0).violations().size();
        boolean structural = bestCount > 0 && options.stream()
                .allMatch(o -> o.violations().stream().anyMatch(GenerationAdvisor::isDataProblem));

        String reason;
        if (bestCount == 0) {
            reason = "Existau combinații valide, dar solverul nu a ajuns la ele — mărește bugetul de timp.";
        } else {
            // the rule that blocks the most candidate placements is the one worth reporting
            Map<String, Integer> tally = new HashMap<>();
            for (UnassignedDiagnostic.PlacementOption o : options) {
                for (String s : o.violations()) {
                    tally.merge(family(s), 1, Integer::sum);
                }
            }
            String dominant = tally.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("necunoscut");
            reason = "Nicio combinație sală + interval nu e complet liberă. Cel mai des blochează: "
                    + dominant + ".";
        }

        return new UnassignedDiagnostic(a.getId(), a.getSubject().getCode(), a.getSubject().getName(),
                a.getActivityType().name(),
                a.getProfessor() == null ? null : a.getProfessor().getName(),
                groupNames, students, reason, structural,
                options.stream().limit(5).toList());
    }

    /** The hard rules the solver enforces, evaluated for one candidate placement. */
    private List<String> violationsFor(ScheduledActivity a, TimeSlot ts, Room room,
                                       List<ScheduledActivity> all, List<BlockedDayRule> blockedDays,
                                       List<SpecialBlockRule> specialBlocks,
                                       List<ProfessorUnavailability> profUnavail,
                                       List<ProfessorRoomRestriction> profRooms) {
        List<String> v = new ArrayList<>();
        int students = a.totalStudentCount();

        // room == null is an online activity: none of the room rules apply to it
        if (room != null) {
            if (students > room.getCapacity()) {
                v.add("capacitate depășită cu " + (students - room.getCapacity()) + " locuri");
            }
            if (a.isRequiresAmphitheater() && room.getTypology() != RoomTypology.AMPHITHEATER) {
                v.add("necesită amfiteatru");
            }
            if (a.isRequiresLab() && room.getTypology() != RoomTypology.LAB) {
                v.add("necesită laborator");
            }
            if (room.getUnavailabilities().stream()
                    .anyMatch(u -> u.blocks(ts.getDayOfWeek(), ts.getStartTime(), ts.getEndTime(),
                            a.getWeekParity()))) {
                v.add("sala indisponibilă");
            }
        }
        if (a.isMaster() && !ts.isEveningModule()) {
            v.add("master doar în modulele de seară");
        }
        for (BlockedDayRule r : blockedDays) {
            if (r.getDayOfWeek() == ts.getDayOfWeek() && a.getStudentGroups().stream().anyMatch(g ->
                    g.getStudyProgram() == r.getStudyProgram() && g.getYear() == r.getYear())) {
                v.add("zi blocată pentru anul " + r.getYear());
                break;
            }
        }
        for (SpecialBlockRule r : specialBlocks) {
            if (r.getTimeSlot() != null && r.getTimeSlot().getId().equals(ts.getId())
                    && a.getSpecialCategory() != r.getCategory() && audienceMatches(a, r)) {
                v.add("interval rezervat pentru " + r.getCategory());
                break;
            }
        }
        if (a.getProfessor() != null && room != null) {
            boolean hasOnlyThis = false;
            boolean matchesOnlyThis = false;
            for (ProfessorRoomRestriction r : profRooms) {
                if (!r.getProfessor().getId().equals(a.getProfessor().getId())) {
                    continue;
                }
                if (r.getRestrictionType() == RestrictionType.FORBIDDEN
                        && r.getRoom().getId().equals(room.getId())) {
                    v.add("sală interzisă pentru acest cadru didactic");
                }
                if (r.getRestrictionType() == RestrictionType.ONLY_THIS) {
                    hasOnlyThis = true;
                    if (r.getRoom().getId().equals(room.getId())) {
                        matchesOnlyThis = true;
                    }
                }
            }
            if (hasOnlyThis && !matchesOnlyThis) {
                v.add("cadrul didactic poate preda doar în altă sală");
            }
        }
        if (a.getProfessor() != null) {
            for (ProfessorUnavailability u : profUnavail) {
                if (u.getProfessor().getId().equals(a.getProfessor().getId())
                        && u.blocks(ts.getDayOfWeek(), ts.getStartTime(), ts.getEndTime())) {
                    v.add("cadrul didactic este indisponibil");
                    break;
                }
            }
        }
        // clashes with activities already occupying this slot
        for (ScheduledActivity o : all) {
            if (o.getId().equals(a.getId()) || !o.isPlaced()) {
                continue;
            }
            if (!o.getTimeSlot().getId().equals(ts.getId()) || !a.parityClashesWith(o)) {
                continue;
            }
            if (room != null && o.getRoom() != null && o.getRoom().getId().equals(room.getId())) {
                v.add("sala e ocupată de „" + o.getSubject().getName() + "”");
            }
            if (a.getProfessor() != null && o.getProfessor() != null
                    && o.getProfessor().getId().equals(a.getProfessor().getId())) {
                v.add("cadrul didactic predă deja „" + o.getSubject().getName() + "”");
            }
            if (!Collections.disjoint(a.getStudentGroups(), o.getStudentGroups())) {
                v.add("grupa are deja „" + o.getSubject().getName() + "”");
            }
        }
        return v;
    }

    private static boolean audienceMatches(ScheduledActivity a, SpecialBlockRule rule) {
        if (rule.getStudentGroup() != null) {
            return a.getStudentGroups().contains(rule.getStudentGroup());
        }
        if (rule.getSpecialization() != null && !rule.getSpecialization().isBlank()
                && rule.getYear() != null) {
            return a.getStudentGroups().stream().anyMatch(g ->
                    rule.getSpecialization().equalsIgnoreCase(g.getSpecialization())
                            && rule.getYear() == g.getYear());
        }
        return false;
    }

    /** Groups a specific message into the rule family it belongs to, for the "most often" tally. */
    private static String family(String violation) {
        if (violation.startsWith("capacitate")) {
            return "capacitatea sălii";
        }
        if (violation.startsWith("sala e ocupată")) {
            return "sălile sunt ocupate";
        }
        if (violation.startsWith("grupa are deja")) {
            return "grupa are deja ore";
        }
        if (violation.startsWith("cadrul didactic predă")) {
            return "cadrul didactic are deja ore";
        }
        return violation;
    }

    /** True for violations that a bigger time budget can never resolve. */
    private static boolean isDataProblem(String violation) {
        return violation.startsWith("capacitate")
                || violation.equals("necesită amfiteatru")
                || violation.equals("sala indisponibilă")
                || violation.startsWith("zi blocată")
                || violation.startsWith("interval rezervat")
                || violation.startsWith("cadrul didactic este indisponibil")
                || violation.startsWith("sală interzisă")
                || violation.startsWith("cadrul didactic poate preda")
                || violation.startsWith("master doar");
    }
}
