package ro.uvt.fsgc.orar.repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.TimeSlot;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    Optional<TimeSlot> findByDayOfWeekAndSlotIndex(DayOfWeek dayOfWeek, int slotIndex);
    List<TimeSlot> findAllByOrderByDayOfWeekAscSlotIndexAsc();
}
