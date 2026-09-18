-- ============================================================
-- Hours fixed by hand
-- ============================================================
-- A professor asks for a particular hour at a particular time: it is placed by hand and pinned,
-- and the next generation builds the rest of the timetable around it instead of starting over.
ALTER TABLE scheduled_activity
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
