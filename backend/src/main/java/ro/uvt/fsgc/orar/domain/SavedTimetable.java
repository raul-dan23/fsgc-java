package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A stored snapshot of the whole timetable: one entry per activity with the slot and room it had
 * at save time. Lets a good generation be kept and restored after later regenerations.
 */
@Entity
@Table(name = "saved_timetable")
@Getter
@Setter
@NoArgsConstructor
public class SavedTimetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Timefold score string as reported by the solver, when the snapshot came from a generation. */
    @Column
    private String score;

    @Column(name = "total_activities", nullable = false)
    private int totalActivities;

    @Column(name = "assigned_count", nullable = false)
    private int assignedCount;

    @OneToMany(mappedBy = "savedTimetable", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SavedTimetableEntry> entries = new ArrayList<>();
}
