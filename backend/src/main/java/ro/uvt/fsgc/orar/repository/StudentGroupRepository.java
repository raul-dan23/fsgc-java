package ro.uvt.fsgc.orar.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.StudentGroup;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {
    Optional<StudentGroup> findByName(String name);
}
