package ro.uvt.fsgc.orar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.uvt.fsgc.orar.domain.BlockedDayRule;

public interface BlockedDayRuleRepository extends JpaRepository<BlockedDayRule, Long> {
}
