-- One person who signs in with several emails (a member allowed as 민식, 민식(2) and 민식(3), say).
-- A super admin groups such emails into one person: a vote on a notice is then one a person,
-- whichever of the emails it comes from, and posts, comments, voter lists and the operation log
-- show the person's name. Access itself stays with each email: grouping grants nothing.
-- An email belongs to one person at most; a person holds two emails or more.
create table if not exists public.access_people (
    id bigserial primary key,
    display_name varchar(100) not null,
    created_by_email varchar(320),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_access_people_display_name check (length(btrim(display_name)) > 0)
);

create table if not exists public.access_person_emails (
    normalized_email varchar(320) primary key,
    person_id bigint not null references public.access_people (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint ck_access_person_emails_normalized check (normalized_email = lower(btrim(normalized_email)))
);

create index if not exists idx_access_person_emails_person
    on public.access_person_emails (person_id);

alter table if exists public.access_people enable row level security;
drop policy if exists no_client_access on public.access_people;
create policy no_client_access on public.access_people as permissive for all to public using (false) with check (false);

alter table if exists public.access_person_emails enable row level security;
drop policy if exists no_client_access on public.access_person_emails;
create policy no_client_access on public.access_person_emails as permissive for all to public using (false) with check (false);
