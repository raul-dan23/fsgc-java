package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tunable soft-constraint weights. A single row (id=1) holds the active configuration so
 * weights are never hardcoded as magic numbers in the ConstraintProvider; they are read
 * from the DB and exposed/editable via the REST API and UI.
 */
@Entity
@Table(name = "constraint_weights")
@Getter
@Setter
@NoArgsConstructor
public class ConstraintWeights {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** Penalty per unit of (busiest day - emptiest day) imbalance, per group. */
    @Column(name = "daily_load_balance", nullable = false)
    private int dailyLoadBalance = 40;

    /** Penalty per empty module gap inside a group's day. */
    @Column(name = "group_gap", nullable = false)
    private int groupGap = 30;

    /** Penalty per late (module 7-8) activity for licenta groups, scaled by lateness. */
    @Column(name = "late_hours_license", nullable = false)
    private int lateHoursLicense = 15;

    /** Penalty encouraging a group's daily activities to be compact / start in the morning. */
    @Column(name = "compactness", nullable = false)
    private int compactness = 8;

    /** Faculty-wide weekly spread penalty. */
    @Column(name = "global_weekly_balance", nullable = false)
    private int globalWeeklyBalance = 5;

    /** Reward weight for honoring professor time preferences. */
    @Column(name = "professor_preference", nullable = false)
    private int professorPreference = 5;

    /**
     * Penalty pulling the two halves of an alternating (SI/SP) hour onto the same slot and room,
     * so they read as a single timetable cell instead of two hours on different days.
     */
    @Column(name = "parity_pair_together", nullable = false)
    private int parityPairTogether = 50;
}
