-- ============================================================
-- A room can be busy every other week
-- ============================================================
-- A11 is taken on Wednesday afternoon in even weeks only. Without this the window had to be
-- blocked every week, which threw away one of the two amphitheatres for half the semester.
-- EVERY_WEEK keeps the meaning of every window entered so far.
ALTER TABLE room_unavailability
    ADD COLUMN week_parity VARCHAR(32) NOT NULL DEFAULT 'EVERY_WEEK';
