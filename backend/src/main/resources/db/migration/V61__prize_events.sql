-- Prize events: a super admin picks a period, the top point earners of that period become the
-- candidates, and confirming the winners records each paid prize as a donation ledger expense.
create table if not exists public.prize_events (
    id bigserial primary key,
    group_id bigint not null references public.groups(id),
    title varchar(100) not null,
    period_start date not null,
    period_end date not null,
    winner_count integer not null,
    status varchar(20) not null,
    confirmed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_prize_events_group
    on public.prize_events (group_id, id desc);

-- normalized_email and nickname are cleared when the winner deletes their account; the ledger
-- expense never carries the winner's name.
create table if not exists public.prize_event_winners (
    id bigserial primary key,
    event_id bigint not null references public.prize_events(id) on delete cascade,
    place integer not null,
    normalized_email varchar(320),
    nickname varchar(100),
    points integer not null,
    prize varchar(100),
    amount bigint not null default 0,
    ledger_expense_id bigint references public.ledger_expense_entries(id) on delete set null,
    created_at timestamptz not null default now(),
    constraint uq_prize_event_winners_place unique (event_id, place)
);

create index if not exists idx_prize_event_winners_email
    on public.prize_event_winners (normalized_email);

alter table if exists public.prize_events enable row level security;
drop policy if exists no_client_access on public.prize_events;
create policy no_client_access on public.prize_events as permissive for all to public using (false) with check (false);

alter table if exists public.prize_event_winners enable row level security;
drop policy if exists no_client_access on public.prize_event_winners;
create policy no_client_access on public.prize_event_winners as permissive for all to public using (false) with check (false);
