package ro.uvt.fsgc.orar.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.Subject;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByCode(String code);
}
