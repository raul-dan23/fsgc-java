-- ============================================================
-- Activities that must be held in a lab
-- ============================================================
-- Mirrors requires_amphitheater: when set, only a room of typology LAB will do. When not set the
-- activity is free to go anywhere, labs included, so this narrows the choice for the few that
-- genuinely need equipment without reserving the labs for everyone else.
ALTER TABLE scheduled_activity
    ADD COLUMN requires_lab BOOLEAN NOT NULL DEFAULT FALSE;
