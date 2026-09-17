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
 * A window in which a room may NOT be used. Rooms are available by default, so only the
 * exceptions are stored — the inverse of the older {@link RoomAvailability}, which required
 * every usable window to be listed explicitly.
 */
@Entity
@Table(name = "room_unavailability")
@Getter
@Setter
@NoArgsConstructor
public class RoomUnavailability {

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

    @Column
    private String reason;

    /**
     * True if this blocked window overlaps [from, to] on the given day. Overlap (not containment)
     * is the right test: any intersection with a blocked window makes the slot unusable.
     */
    public boolean overlaps(DayOfWeek day, LocalTime from, LocalTime to) {
        return dayOfWeek == day && from.isBefore(endTime) && to.isAfter(startTime);
    }
}
