create table if not exists public.notices (
    id bigserial primary key,
    group_id bigint not null references public.groups(id) on delete cascade,
    title varchar(200) not null,
    content text not null,
    author_email varchar(320) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_notices_group_created
    on public.notices (group_id, created_at desc, id desc);

alter table if exists public.notices enable row level security;

drop policy if exists no_client_access on public.notices;
create policy no_client_access
    on public.notices
    as permissive
    for all
    to public
    using (false)
    with check (false);
