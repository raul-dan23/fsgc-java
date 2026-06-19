package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A building / address. Used by the "consecutive-slots same building" hard constraint:
 * a group cannot have back-to-back activities in different buildings without a free slot
 * for commuting (e.g. Str. Paris vs Blvd. Parvan).
 */
@Entity
@Table(name = "building")
@Getter
@Setter
@NoArgsConstructor
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String address;

    public Building(String name, String address) {
        this.name = name;
        this.address = address;
    }
}