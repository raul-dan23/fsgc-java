package ro.uvt.fsgc.orar.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByName(String name);
}
