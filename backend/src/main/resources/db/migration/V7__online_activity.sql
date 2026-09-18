-- ============================================================
-- Online activities
-- ============================================================
-- An activity marked online still gets a time slot (and still respects professor, group and
-- blocked-interval rules), but it never gets a room: nobody travels for it, so it does not
-- occupy a room and does not clash with what happens in the rooms at that hour.
ALTER TABLE scheduled_activity
    ADD COLUMN online BOOLEAN NOT NULL DEFAULT FALSE;
