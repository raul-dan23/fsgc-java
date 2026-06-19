package ro.uvt.fsgc.orar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.RoomAvailability;

public interface RoomAvailabilityRepository extends JpaRepository<RoomAvailability, Long> {
}
