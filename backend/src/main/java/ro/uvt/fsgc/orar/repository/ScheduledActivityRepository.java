package ro.uvt.fsgc.orar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;

public interface ScheduledActivityRepository extends JpaRepository<ScheduledActivity, Long> {
}
