package ro.uvt.fsgc.orar.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.SavedTimetable;

public interface SavedTimetableRepository extends JpaRepository<SavedTimetable, Long> {
    List<SavedTimetable> findAllByOrderByCreatedAtDesc();
}
