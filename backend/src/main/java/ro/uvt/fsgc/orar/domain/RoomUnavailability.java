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
     * The weeks the window applies to. EVERY_WEEK is the usual case; ODD_WEEKS / EVEN_WEEKS is for
     * a room taken every other week, which would otherwise have to be blocked for the whole term.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "week_parity", nullable = false)
    private WeekParity weekParity = WeekParity.EVERY_WEEK;

    /**
     * True if this blocked window overlaps [from, to] on the given day. Overlap (not containment)
     * is the right test: any intersection with a blocked window makes the slot unusable.
     *
     * <p>Kept for callers that have no activity at hand; it ignores the week parity, so it answers
     * "is this window ever in the way", which is the safe side to err on.
     */
    public boolean overlaps(DayOfWeek day, LocalTime from, LocalTime to) {
        return dayOfWeek == day && from.isBefore(endTime) && to.isAfter(startTime);
    }

    /**
     * The same question for an hour that runs on certain weeks: a window that only applies on even
     * weeks does not stand in the way of an hour held on odd ones.
     */
    public boolean blocks(DayOfWeek day, LocalTime from, LocalTime to, WeekParity activityParity) {
        if (!overlaps(day, from, to)) {
            return false;
        }
        return weekParity == WeekParity.EVERY_WEEK
                || activityParity == WeekParity.EVERY_WEEK
                || weekParity == activityParity;
    }
}
