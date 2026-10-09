-- A member's request to have their nickname changed. It is a ticket and nothing more: an admin
-- marks it APPROVED once they have changed the nickname by hand (player management, access list)
-- or REJECTED, with an optional note; marking it changes no nickname anywhere.
-- current_nickname is the nickname the account showed when it asked, kept so the ticket still
-- reads right after the change. A person has at most one PENDING request for a group.
-- Requests are removed with the account that made them. Adds one table.
create table if not exists public.nickname_change_requests (
    id bigserial primary key,
    group_id bigint not null references public.groups(id) on delete cascade,
    requester_email varchar(320) not null,
    current_nickname varchar(100),
    desired_nickname varchar(50) not null,
    reason varchar(300),
    status varchar(20) not null default 'PENDING',
    admin_note varchar(300),
    processed_by_email varchar(320),
    processed_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ck_nickname_change_requests_status
        check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED'))
);

create unique index if not exists uq_nickname_change_requests_pending
    on public.nickname_change_requests (group_id, requester_email)
    where status = 'PENDING';

create index if not exists idx_nickname_change_requests_group_created
    on public.nickname_change_requests (group_id, created_at desc, id desc);

create index if not exists idx_nickname_change_requests_requester
    on public.nickname_change_requests (requester_email);

alter table if exists public.nickname_change_requests enable row level security;
drop policy if exists no_client_access on public.nickname_change_requests;
create policy no_client_access on public.nickname_change_requests as permissive for all to public using (false) with check (false);
