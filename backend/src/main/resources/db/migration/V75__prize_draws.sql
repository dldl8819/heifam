-- Prize draws run on the pinball page: an admin puts names in, the balls run in the admin's
-- browser, and what is kept here is the record of it: the title, how many took part, and who won
-- which place and prize. The draw itself is not stored and cannot be replayed.
-- A winner picked from the roster keeps the player's id, so the name written here is blanked with
-- the player's other traces when that member leaves; a name typed by hand has none.
-- Adds two tables.
create table if not exists public.prize_draws (
    id bigserial primary key,
    group_id bigint not null references public.groups(id) on delete cascade,
    title varchar(100) not null,
    -- FIRST: the first balls to arrive win. LAST: the last ones do.
    mode varchar(10) not null,
    entrant_count integer not null,
    created_by_email varchar(320),
    created_at timestamptz not null default now(),
    constraint ck_prize_draws_mode check (mode in ('FIRST', 'LAST')),
    constraint ck_prize_draws_entrants check (entrant_count >= 2)
);

create index if not exists idx_prize_draws_group
    on public.prize_draws (group_id, id desc);

create table if not exists public.prize_draw_winners (
    id bigserial primary key,
    draw_id bigint not null references public.prize_draws(id) on delete cascade,
    place integer not null,
    name varchar(50) not null,
    player_id bigint references public.players(id) on delete set null,
    prize varchar(100),
    constraint uq_prize_draw_winners_place unique (draw_id, place)
);

create index if not exists idx_prize_draw_winners_player
    on public.prize_draw_winners (player_id);

alter table if exists public.prize_draws enable row level security;
drop policy if exists no_client_access on public.prize_draws;
create policy no_client_access on public.prize_draws as permissive for all to public using (false) with check (false);

alter table if exists public.prize_draw_winners enable row level security;
drop policy if exists no_client_access on public.prize_draw_winners;
create policy no_client_access on public.prize_draw_winners as permissive for all to public using (false) with check (false);
