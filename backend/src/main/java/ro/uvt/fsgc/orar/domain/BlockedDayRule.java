package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A whole weekday blocked for a terminal year (e.g. licenta year 3, Friday) so students
 * can prepare their final thesis. Editable per semester via the UI.
 */
@Entity
@Table(name = "blocked_day_rule")
@Getter
@Setter
@NoArgsConstructor
public class BlockedDayRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "study_program", nullable = false)
    private StudyProgram studyProgram;

    @Column(nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column
    private String semester;

    @Column(name = "academic_year")
    private String academicYear;
}
