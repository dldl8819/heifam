-- A prize draw can be run with the cannon as well as the pinball: each shot draws one winner among
-- the entrants. Only the check on prize_draws.mode changes; no rows are touched.
alter table public.prize_draws drop constraint if exists ck_prize_draws_mode;
alter table public.prize_draws
    add constraint ck_prize_draws_mode check (mode in ('FIRST', 'LAST', 'CANNON'));
