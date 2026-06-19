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

@Entity
@Table(name = "professor")
@Getter
@Setter
@NoArgsConstructor
public class Professor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String email;

    /** Didactic title, e.g. "conf.dr.", "lect.dr.". */
    @Column
    private String title;

    @Column
    private String department;

    /**
     * Whether the professor owns a laptop. If false, they can only teach in rooms
     * equipped with a desktop computer (see room equipment + the professor-room constraint).
     */
    @Column(name = "has_own_laptop", nullable = false)
    private boolean hasOwnLaptop = true;
}