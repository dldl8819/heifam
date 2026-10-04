-- Free win predictions: members pick a team while a match waits for its result, and a correct pick
-- earns points. Nothing is staked. Predictions close a few minutes after the match is created,
-- when an admin closes them, or when the result is recorded, whichever comes first.
alter table public.matches
    add column if not exists predictions_closed_at timestamptz;

create table if not exists public.match_predictions (
    id bigserial primary key,
    match_id bigint not null references public.matches(id) on delete cascade,
    predictor_email varchar(320) not null,
    predicted_team varchar(10) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_match_predictions_predictor unique (match_id, predictor_email)
);

create index if not exists idx_match_predictions_predictor
    on public.match_predictions (predictor_email, match_id desc);

alter table if exists public.match_predictions enable row level security;
drop policy if exists no_client_access on public.match_predictions;
create policy no_client_access on public.match_predictions as permissive for all to public using (false) with check (false);
