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
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A professor's unavailability. If {@link #startTime}/{@link #endTime} are null the whole
 * {@link #dayOfWeek} is blocked; otherwise only that interval on that day is blocked.
 */
@Entity
@Table(name = "professor_unavailability")
@Getter
@Setter
@NoArgsConstructor
public class ProfessorUnavailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private Professor professor;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** True if the activity's [from,to] on the given day falls inside this unavailability. */
    public boolean blocks(DayOfWeek day, LocalTime from, LocalTime to) {
        if (dayOfWeek != day) {
            return false;
        }
        if (startTime == null || endTime == null) {
            return true; // whole day blocked
        }
        // overlap of [from,to] with [startTime,endTime]
        return from.isBefore(endTime) && to.isAfter(startTime);
    }
}
