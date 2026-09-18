-- ============================================================
-- Walking time between P01 and the rest of the faculty
-- ============================================================
-- P01 is about 20 minutes' walk away, and two modules are only 10 minutes apart, so a group
-- sent from P01 straight into another room (or the other way round) physically cannot make it.
-- Soft rather than hard: with a single small room out there, a hard rule could leave hours
-- unplaced, and the staff would rather see the clash than lose the hour.
ALTER TABLE constraint_weights
    ADD COLUMN far_room_commute INTEGER NOT NULL DEFAULT 80;

UPDATE constraint_weights SET far_room_commute = 80 WHERE id = 1;
