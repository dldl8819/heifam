-- A prize event ranked by numbers counted outside the site (MANUAL, e.g. chat messages from a chat
-- analysis app): the admin who starts it enters each player's number, kept here, and names the unit.
-- Only players are kept, never a name, so nothing here outlives a player's identity.
alter table public.prize_events
    add column if not exists unit_label varchar(20);

create table if not exists public.prize_event_entries (
    id bigserial primary key,
    event_id bigint not null references public.prize_events(id) on delete cascade,
    player_id bigint not null references public.players(id) on delete cascade,
    score bigint not null,
    constraint uq_prize_event_entries_player unique (event_id, player_id)
);

alter table if exists public.prize_event_entries enable row level security;
drop policy if exists no_client_access on public.prize_event_entries;
create policy no_client_access on public.prize_event_entries as permissive for all to public using (false) with check (false);
