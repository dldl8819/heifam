-- 휴면: an admin sets a player aside for a while without hiding who they were. Unlike the
-- deactivation (비활성), which closes the account and shows the player as a withdrawn member,
-- a dormant player's nickname stays on their past results; the roster, the ranking and new
-- balances and matches leave them out until an admin wakes them.
-- Null while the player is on the roster, the time they were set aside otherwise.
alter table public.players add column if not exists dormant_at timestamptz;
