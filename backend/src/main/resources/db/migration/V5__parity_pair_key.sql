-- ============================================================
-- Alternating (SI/SP) activities that came from one Excel cell
-- ============================================================
-- A combined "activitate" value such as "Seminar(SI)/Seminar(SP)" describes ONE weekly hour
-- shared by two halves: one group attends on odd weeks, the other on even weeks. Both halves
-- now carry the same parity_pair_key so the solver can keep them in the same slot and room,
-- which is how the hour appears in the real FSGC timetable (a single cell split SI/SP).
ALTER TABLE scheduled_activity ADD COLUMN parity_pair_key VARCHAR(255);

-- Only the paired halves are looked up together; the column is null for ordinary activities.
CREATE INDEX idx_activity_parity_pair ON scheduled_activity (parity_pair_key)
    WHERE parity_pair_key IS NOT NULL;

-- Backfill for data imported before this column existed, so the new rule applies without a
-- re-import (a re-import would wipe the manually configured rules). Purely additive: it only
-- links halves that are already unambiguous — exactly two activities for one subject and type,
-- carrying the two opposite parities. Nothing else is touched; in particular the audience of
-- each half is left exactly as imported.
UPDATE scheduled_activity a
SET parity_pair_key = 'backfill#' || p.subject_id || '#' || p.activity_type
FROM (SELECT subject_id, activity_type
      FROM scheduled_activity
      WHERE week_parity <> 'EVERY_WEEK'
      GROUP BY subject_id, activity_type
      HAVING count(*) = 2 AND count(DISTINCT week_parity) = 2) p
WHERE a.subject_id = p.subject_id
  AND a.activity_type = p.activity_type
  AND a.week_parity <> 'EVERY_WEEK';

-- Weight for the new soft constraint that pulls the two halves onto the same slot/room.
-- Higher than the day-balance weight (40): splitting an alternating hour across two different
-- days is more jarring to read than a slightly uneven week.
ALTER TABLE constraint_weights
    ADD COLUMN parity_pair_together INTEGER NOT NULL DEFAULT 50;

UPDATE constraint_weights SET parity_pair_together = 50 WHERE id = 1;
