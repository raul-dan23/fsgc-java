package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reserves a (day + module) interval exclusively for a special category (DPPD/CCOC/DCT/
 * limbi straine) for a given group or specialization+year. In that interval no normal
 * activity may be scheduled for that audience. Either {@link #studentGroup} or the
 * {@link #specialization}+{@link #year} pair identifies the audience.
 */
@Entity
@Table(name = "special_block_rule")
@Getter
@Setter
@NoArgsConstructor
public class SpecialBlockRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_group_id")
    private StudentGroup studentGroup;

    @Column
    private String specialization;

    @Column
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpecialCategory category;

    @Column
    private String semester;

    @Column(name = "academic_year")
    private String academicYear;
}
