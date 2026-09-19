package ro.uvt.fsgc.orar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.ProfessorRoomRestriction;

public interface ProfessorRoomRestrictionRepository extends JpaRepository<ProfessorRoomRestriction, Long> {
}
