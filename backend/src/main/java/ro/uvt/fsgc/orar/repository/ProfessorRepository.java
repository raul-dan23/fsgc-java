package ro.uvt.fsgc.orar.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.Professor;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    Optional<Professor> findByName(String name);
}
