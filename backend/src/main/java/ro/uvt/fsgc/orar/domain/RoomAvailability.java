package ro.uvt.fsgc.orar.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * One availability window of a room for a given weekday. A room with a single
 * "08:00-20:00 Mon-Fri" availability produces 5 rows; a room available only on
 * some days/intervals produces exactly the rows that apply.
 */
@Entity
@Table(name = "room_availability")
@Getter
@Setter
@NoArgsConstructor
public class RoomAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @JsonIgnore
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public RoomAvailability(Room room, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.room = room;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** True if this window fully covers [from, to] on the given day. */
    public boolean covers(DayOfWeek day, LocalTime from, LocalTime to) {
        return dayOfWeek == day && !from.isBefore(startTime) && !to.isAfter(endTime);
    }
}