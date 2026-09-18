package ro.uvt.fsgc.orar.service;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.uvt.fsgc.orar.domain.ConstraintWeights;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.repository.BlockedDayRuleRepository;
import ro.uvt.fsgc.orar.repository.ConstraintWeightsRepository;
import ro.uvt.fsgc.orar.repository.ProfessorUnavailabilityRepository;
import ro.uvt.fsgc.orar.repository.RoomRepository;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.SpecialBlockRuleRepository;
import ro.uvt.fsgc.orar.repository.StudentGroupRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;
import ro.uvt.fsgc.orar.solver.TimetableConstraintConfiguration;
import ro.uvt.fsgc.orar.solver.TimetableSolution;

/**
 * Transactional boundary for loading the solver problem and writing the solution back. Kept in a
 * separate bean so the {@code @Transactional} methods are invoked through the Spring proxy: the
 * whole problem is read in ONE persistence context, guaranteeing shared entity instances for the
 * solver's identity-based constraint joins.
 */
@Service
public class TimetableDataService {

    private final ScheduledActivityRepository activityRepo;
    private final TimeSlotRepository timeSlotRepo;
    private final RoomRepository roomRepo;
    private final StudentGroupRepository groupRepo;
    private final BlockedDayRuleRepository blockedDayRepo;
    private final SpecialBlockRuleRepository specialBlockRepo;
    private final ProfessorUnavailabilityRepository profUnavailRepo;
    private final ConstraintWeightsRepository weightsRepo;

    public TimetableDataService(ScheduledActivityRepository activityRepo, TimeSlotRepository timeSlotRepo,
                                RoomRepository roomRepo, StudentGroupRepository groupRepo,
                                BlockedDayRuleRepository blockedDayRepo, SpecialBlockRuleRepository specialBlockRepo,
                                ProfessorUnavailabilityRepository profUnavailRepo,
                                ConstraintWeightsRepository weightsRepo) {
        this.activityRepo = activityRepo;
        this.timeSlotRepo = timeSlotRepo;
        this.roomRepo = roomRepo;
        this.groupRepo = groupRepo;
        this.blockedDayRepo = blockedDayRepo;
        this.specialBlockRepo = specialBlockRepo;
        this.profUnavailRepo = profUnavailRepo;
        this.weightsRepo = weightsRepo;
    }

    @Transactional(readOnly = true)
    public TimetableSolution loadProblem() {
        TimetableSolution s = new TimetableSolution();
        s.setTimeSlots(timeSlotRepo.findAll());
        s.setRooms(roomRepo.findAll());
        s.setStudentGroups(groupRepo.findAll());
        s.setActivities(activityRepo.findAll());
        s.setBlockedDayRules(blockedDayRepo.findAll());
        s.setSpecialBlockRules(specialBlockRepo.findAll());
        s.setProfessorUnavailabilities(profUnavailRepo.findAll());

        // Touch lazy collections and clear any previous assignment so the solver starts fresh —
        // except for the hours someone pinned, which keep theirs and anchor the rest of the solve.
        // A pin without a placement would mean "pinned nowhere", i.e. never scheduled, so it is
        // ignored here; the API does not let one be created, but old data might carry one.
        s.getActivities().forEach(a -> {
            a.getStudentGroups().size();
            if (a.isPinned() && a.isPlaced()) {
                return;
            }
            a.setPinned(false);
            a.setTimeSlot(null);
            a.setRoom(null);
        });
        s.getRooms().forEach(r -> {
            r.getUnavailabilities().size();
            r.getEquipment().size();
        });

        s.setConstraintConfiguration(buildWeights());
        return s;
    }

    private TimetableConstraintConfiguration buildWeights() {
        ConstraintWeights w = weightsRepo.findById(ConstraintWeights.SINGLETON_ID)
                .orElseGet(ConstraintWeights::new);
        TimetableConstraintConfiguration c = new TimetableConstraintConfiguration();
        c.setDailyLoadBalance(HardMediumSoftScore.ofSoft(w.getDailyLoadBalance()));
        c.setGroupGap(HardMediumSoftScore.ofSoft(w.getGroupGap()));
        c.setLateHoursLicense(HardMediumSoftScore.ofSoft(w.getLateHoursLicense()));
        c.setCompactness(HardMediumSoftScore.ofSoft(w.getCompactness()));
        c.setGlobalWeeklyBalance(HardMediumSoftScore.ofSoft(w.getGlobalWeeklyBalance()));
        c.setProfessorPreference(HardMediumSoftScore.ofSoft(w.getProfessorPreference()));
        c.setParityPairTogether(HardMediumSoftScore.ofSoft(w.getParityPairTogether()));
        c.setRoomOversize(HardMediumSoftScore.ofSoft(w.getRoomOversize()));
        c.setFarRoomCommute(HardMediumSoftScore.ofSoft(w.getFarRoomCommute()));
        c.setProfessorWeekDays(HardMediumSoftScore.ofSoft(w.getProfessorWeekDays()));
        return c;
    }

    @Transactional
    public void persistSolution(TimetableSolution solved) {
        Map<Long, TimeSlot> tsById = new HashMap<>();
        timeSlotRepo.findAll().forEach(t -> tsById.put(t.getId(), t));
        Map<Long, Room> roomById = new HashMap<>();
        roomRepo.findAll().forEach(r -> roomById.put(r.getId(), r));

        for (ScheduledActivity solvedAct : solved.getActivities()) {
            activityRepo.findById(solvedAct.getId()).ifPresent(managed -> {
                managed.setTimeSlot(solvedAct.getTimeSlot() == null ? null
                        : tsById.get(solvedAct.getTimeSlot().getId()));
                managed.setRoom(solvedAct.getRoom() == null ? null
                        : roomById.get(solvedAct.getRoom().getId()));
                activityRepo.save(managed);
            });
        }
    }
}
