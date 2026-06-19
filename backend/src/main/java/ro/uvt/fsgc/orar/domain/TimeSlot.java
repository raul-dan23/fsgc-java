package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One of the 40 fixed timetable slots (Mon-Fri x 8 modules). Seeded by Flyway,
 * never imported. The id is deterministic: dayIndex(1..5)*10 + slotIndex(1..8).
 */
@Entity
@Table(name = "time_slot")
@Getter
@Setter
@NoArgsConstructor
public class TimeSlot {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    /** 1..8, the module index within a day. */
    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public TimeSlot(Long id, DayOfWeek dayOfWeek, int slotIndex, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.slotIndex = slotIndex;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** True for master-allowed modules (6,7,8 -> 16:20-21:10). */
    public boolean isEveningModule() {
        return slotIndex >= 6;
    }
}
