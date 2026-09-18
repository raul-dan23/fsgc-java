-- ============================================================
-- A professor's day, instead of a professor's rooms
-- ============================================================
-- The room restrictions (ONLY_THIS / FORBIDDEN per professor) were never used: no row was ever
-- entered in either database. What the staff actually needs is the shape of a teaching day --
-- no scattered hours, and not five trips to the faculty for one module each -- so the table goes
-- and two rules about the day take its place.
DROP TABLE IF EXISTS professor_room_restriction;

-- Soft: how hard to try to fit a professor's week into at most three days.
ALTER TABLE constraint_weights
    ADD COLUMN professor_week_days INTEGER NOT NULL DEFAULT 60;

UPDATE constraint_weights SET professor_week_days = 60 WHERE id = 1;
