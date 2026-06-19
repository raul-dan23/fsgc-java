package ro.uvt.fsgc.orar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.ProfessorUnavailability;

public interface ProfessorUnavailabilityRepository extends JpaRepository<ProfessorUnavailability, Long> {
}
