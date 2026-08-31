create table if not exists public.ledger_expense_entries (
    id bigserial primary key,
    group_id bigint not null references public.groups(id) on delete cascade,
    entry_date date not null,
    expense_type varchar(10) not null,
    category varchar(50) not null,
    target varchar(300),
    amount bigint not null,
    memo varchar(500),
    author_email varchar(320) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_ledger_expense_type check (expense_type in ('FIXED', 'VARIABLE')),
    constraint chk_ledger_expense_amount_positive check (amount > 0)
);

create index if not exists idx_ledger_expense_group_date
    on public.ledger_expense_entries (group_id, entry_date desc, id desc);

create index if not exists idx_ledger_expense_group_type_category
    on public.ledger_expense_entries (group_id, expense_type, category);

alter table if exists public.ledger_expense_entries enable row level security;

drop policy if exists no_client_access on public.ledger_expense_entries;
create policy no_client_access
    on public.ledger_expense_entries
    as permissive
    for all
    to public
    using (false)
    with check (false);
