package ro.uvt.fsgc.orar.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single teaching activity that must be placed in the timetable. This is BOTH a
 * JPA entity (persisted from the import) AND the Timefold {@link PlanningEntity}:
 * the solver assigns its {@link #timeSlot} and {@link #room} planning variables.
 *
 * <p>One Excel "Discipline" row can yield more than one ScheduledActivity:
 * combined values like "Curs(SI)/Seminar(SP)" produce two activities with different
 * {@link WeekParity} (SI=odd, SP=even).
 */
@Entity
@Table(name = "scheduled_activity")
@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ScheduledActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "professor_id")
    private Professor professor;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    /** Groups attending. More than one for common (trunchi comun) courses. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "activity_student_group",
            joinColumns = @JoinColumn(name = "activity_id"),
            inverseJoinColumns = @JoinColumn(name = "student_group_id"))
    private Set<StudentGroup> studentGroups = new HashSet<>();

    @Column(name = "duration_in_slots", nullable = false)
    private int durationInSlots = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_parity", nullable = false)
    private WeekParity weekParity = WeekParity.EVERY_WEEK;

    @Enumerated(EnumType.STRING)
    @Column(name = "special_category", nullable = false)
    private SpecialCategory specialCategory = SpecialCategory.NORMAL;

    /** True when the original activitate value was "Curs Amfiteatru". */
    @Column(name = "requires_amphitheater", nullable = false)
    private boolean requiresAmphitheater = false;

    /**
     * Fixed by hand: the solver may not move it. Its slot and room are taken as given and the rest
     * of the timetable is built around them — that is how a professor's "this hour, this time"
     * request survives a regeneration. Only an activity that already has a place can be pinned.
     */
    @PlanningPin
    @Column(nullable = false)
    private boolean pinned = false;

    /**
     * Must be held in a room of typology LAB. An activity without this flag is free to go
     * anywhere, a lab included — the flag narrows the choice, it does not reserve the labs.
     */
    @Column(name = "requires_lab", nullable = false)
    private boolean requiresLab = false;

    /**
     * Held online: the activity still takes a time slot and still obeys every rule about people
     * (professor clashes and unavailabilities, group clashes, blocked days and reserved intervals),
     * but it never takes a room. Two online hours — or an online hour and an on-site one — may
     * therefore share the same module, as long as no group and no professor is in both.
     */
    @Column(nullable = false)
    private boolean online = false;

    /** Original Excel activitate value, kept for traceability and UI display. */
    @Column(name = "raw_type")
    private String rawType;

    /**
     * Links the alternating halves that came from one combined Excel cell (e.g. the SI and SP
     * halves of "Seminar(SI)/Seminar(SP)"). Both halves share the same key, so the solver can
     * recognize them as one weekly hour and keep them in the same slot and room. Null for an
     * ordinary activity that has no alternating twin.
     */
    @Column(name = "parity_pair_key")
    private String parityPairKey;

    // ---- Planning variables (assigned by the solver) ----

    @PlanningVariable(valueRangeProviderRefs = "timeSlotRange", allowsUnassigned = true)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "time_slot_id")
    private TimeSlot timeSlot;

    @PlanningVariable(valueRangeProviderRefs = "roomRange", allowsUnassigned = true)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id")
    private Room room;

    /**
     * True when the activity has its place in the timetable: a slot, plus a room unless it is
     * online. Everything that counts placements (exports, the unassigned list, the solver's
     * medium score) asks this instead of testing the two planning variables by hand.
     */
    public boolean isPlaced() {
        return timeSlot != null && (online || room != null);
    }

    /** Sum of student counts across all attending groups (common-course aware). */
    public int totalStudentCount() {
        return studentGroups.stream().mapToInt(StudentGroup::getStudentCount).sum();
    }

    /** True if any attending group is a master program. */
    public boolean isMaster() {
        return studentGroups.stream().anyMatch(g -> g.getStudyProgram() == StudyProgram.MASTER);
    }

    /** True if two activities can never run at the same slot together (parity overlap). */
    public boolean parityClashesWith(ScheduledActivity other) {
        WeekParity a = this.weekParity;
        WeekParity b = other.weekParity;
        if (a == WeekParity.EVERY_WEEK || b == WeekParity.EVERY_WEEK) {
            return true;
        }
        return a == b;
    }
}
