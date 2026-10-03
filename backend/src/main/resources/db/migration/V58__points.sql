-- Points earned for activity. point_transactions is append-only: balances and rankings are sums,
-- and a take-back is a new negative row. One row per account is locked to keep daily caps exact.
create table if not exists public.point_accounts (
    id bigserial primary key,
    normalized_email varchar(320) not null unique,
    created_at timestamptz not null default now()
);

create table if not exists public.point_transactions (
    id bigserial primary key,
    account_id bigint not null references public.point_accounts(id) on delete cascade,
    reason varchar(40) not null,
    amount integer not null,
    reference_key varchar(80) not null,
    kst_date date not null,
    memo varchar(200),
    created_by_email varchar(320),
    created_at timestamptz not null default now(),
    constraint uq_point_transactions_reference unique (account_id, reason, reference_key)
);

create index if not exists idx_point_transactions_account_date
    on public.point_transactions (account_id, kst_date);
create index if not exists idx_point_transactions_kst_date
    on public.point_transactions (kst_date);
create index if not exists idx_point_transactions_reason_reference
    on public.point_transactions (reason, reference_key);

alter table if exists public.point_accounts enable row level security;
drop policy if exists no_client_access on public.point_accounts;
create policy no_client_access on public.point_accounts as permissive for all to public using (false) with check (false);

alter table if exists public.point_transactions enable row level security;
drop policy if exists no_client_access on public.point_transactions;
create policy no_client_access on public.point_transactions as permissive for all to public using (false) with check (false);
