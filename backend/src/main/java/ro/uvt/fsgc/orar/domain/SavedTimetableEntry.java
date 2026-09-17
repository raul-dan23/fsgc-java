package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One activity's placement inside a {@link SavedTimetable}. The three ids are plain columns, not
 * foreign keys, so a snapshot stays readable after the base data changes; unresolvable ids are
 * skipped when restoring.
 */
@Entity
@Table(name = "saved_timetable_entry")
@Getter
@Setter
@NoArgsConstructor
public class SavedTimetableEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saved_timetable_id", nullable = false)
    private SavedTimetable savedTimetable;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "time_slot_id")
    private Long timeSlotId;

    @Column(name = "room_id")
    private Long roomId;

    public SavedTimetableEntry(SavedTimetable parent, Long activityId, Long timeSlotId, Long roomId) {
        this.savedTimetable = parent;
        this.activityId = activityId;
        this.timeSlotId = timeSlotId;
        this.roomId = roomId;
    }
}
