-- A prize event ranks its period by points earned (POINTS) or by matches played (GAMES). A GAMES
-- winner is a player, not a point account: player_id keeps who it was, and the nickname copy is
-- cleared when that player's identity is removed, as an email winner's is on withdrawal.
alter table public.prize_events
    add column if not exists metric varchar(20) not null default 'POINTS';

alter table public.prize_event_winners
    add column if not exists player_id bigint references public.players(id) on delete set null;

create index if not exists idx_prize_event_winners_player
    on public.prize_event_winners (player_id);
