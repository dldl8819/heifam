-- Members a super admin trusts to change match winners, alongside the admins who always can.
create table if not exists public.match_result_editor_emails (
    id bigserial primary key,
    email varchar(320) not null,
    normalized_email varchar(320) not null unique,
    created_by_email varchar(320),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table if exists public.match_result_editor_emails enable row level security;

drop policy if exists no_client_access on public.match_result_editor_emails;
create policy no_client_access
    on public.match_result_editor_emails
    as permissive
    for all
    to public
    using (false)
    with check (false);

-- The actor's role when the log was written, so admins can be shown only what editors did.
-- Older rows stay null and remain visible to super admins only.
alter table public.operation_audit_logs
    add column if not exists actor_role varchar(20);

create index if not exists idx_operation_audit_logs_actor_role_created_at
    on public.operation_audit_logs (actor_role, created_at desc, id desc);
