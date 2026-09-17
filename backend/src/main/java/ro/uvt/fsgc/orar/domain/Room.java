package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "room")
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String department;

    /** Floor is free text in the source ("Parter", "1", "5", "6"). */
    @Column
    private String floor;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomTypology typology = RoomTypology.SEMINAR;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "building_id")
    private Building building;

    /** Equipment tags, e.g. "computer_catedra", "proiector". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_equipment", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "equipment")
    private Set<String> equipment = new HashSet<>();

    /** Non-uniform per-day availability windows, as recorded by the Excel import. */
    @OneToMany(mappedBy = "room", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<RoomAvailability> availabilities = new ArrayList<>();

    /**
     * Windows in which the room may NOT be used. This is what the solver reads: a room with no
     * rows here is usable on every module. A Set (not a List) so Hibernate does not see two
     * eager bags on this entity.
     */
    // No cascade/orphanRemoval on purpose: with both collections eager, a cascade here re-saved
    // a child that had just been deleted through its own repository. Rows are removed by the
    // DB-level ON DELETE CASCADE when the room itself goes.
    @OneToMany(mappedBy = "room", fetch = FetchType.EAGER)
    private Set<RoomUnavailability> unavailabilities = new HashSet<>();

    @Column(name = "usage_restrictions")
    private String usageRestrictions;
}