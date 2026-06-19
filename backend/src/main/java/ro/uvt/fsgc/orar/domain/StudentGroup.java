package ro.uvt.fsgc.orar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student group (cod_grupa) or, when a section/year has no explicit groups,
 * a whole section-year acting as a single group. {@code name} is the unique key
 * used to resolve references from the Discipline sheet's set_studenti column
 * (e.g. "RISE1 - Grupa 1", "AP1").
 */
@Entity
@Table(name = "student_group")
@Getter
@Setter
@NoArgsConstructor
public class StudentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique reference key: cod_grupa if present, else "<sectie><an>" (e.g. "AP1"). */
    @Column(nullable = false, unique = true)
    private String name;

    /** sectie, e.g. "RISE". */
    @Column(nullable = false)
    private String specialization;

    @Column(nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(name = "study_program", nullable = false)
    private StudyProgram studyProgram;

    /** nr_studenti_grupa if present, else nr_studenti_an. */
    @Column(name = "student_count", nullable = false)
    private int studentCount;

    @Column
    private String department;
}