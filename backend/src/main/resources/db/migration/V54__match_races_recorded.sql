alter table public.matches
    add column if not exists races_recorded boolean not null default false;

-- Balance-page results have sent each player's race since recorded races went live, so those
-- matches are already recorded; everything before, and every manual entry so far, stays guessed.
update public.matches
set races_recorded = true
where source = 'BALANCED'
    and winning_team is not null
    and played_at >= timestamptz '2026-09-27 22:13:04+09';
