-- Server and hosting bills (e.g. Render), tracked apart from the donation expense ledger.
-- A member usually pays them on a personal card first. reimbursed_date marks when the donation
-- account paid that member back, which is the only point at which a bill reduces the donation balance.
create table if not exists public.ledger_server_costs (
    id bigserial primary key,
    group_id bigint not null references public.groups(id) on delete cascade,
    service_name varchar(50) not null,
    billing_month varchar(7) not null,
    charged_date date not null,
    usd_amount numeric(10, 2),
    krw_amount bigint,
    paid_by varchar(100),
    reimbursed_date date,
    memo varchar(500),
    author_email varchar(320) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_ledger_server_cost_billing_month check (billing_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    constraint chk_ledger_server_cost_usd_non_negative check (usd_amount is null or usd_amount >= 0),
    constraint chk_ledger_server_cost_krw_positive check (krw_amount is null or krw_amount > 0),
    constraint chk_ledger_server_cost_reimbursed_needs_krw check (reimbursed_date is null or krw_amount is not null)
);

create index if not exists idx_ledger_server_costs_group_month
    on public.ledger_server_costs (group_id, billing_month desc, id desc);

alter table if exists public.ledger_server_costs enable row level security;

drop policy if exists no_client_access on public.ledger_server_costs;
create policy no_client_access
    on public.ledger_server_costs
    as permissive
    for all
    to public
    using (false)
    with check (false);
