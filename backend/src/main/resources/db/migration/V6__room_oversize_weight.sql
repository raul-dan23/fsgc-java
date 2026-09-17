-- ============================================================
-- Right-sizing rooms
-- ============================================================
-- Capacity was only bounded from below: a room had to hold the students, but nothing discouraged
-- a 27-student seminar from taking a 150-seat amphitheatre. This weight penalizes every empty
-- seat, so activities drift to the smallest room that fits and the two amphitheatres stay free
-- for the trunchi-comun courses that genuinely need them.
ALTER TABLE constraint_weights
    ADD COLUMN room_oversize INTEGER NOT NULL DEFAULT 2;

UPDATE constraint_weights SET room_oversize = 2 WHERE id = 1;
