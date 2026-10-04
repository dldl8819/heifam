-- Notices for members: admins can keep a notice to admins, and members leave reads, likes and
-- comments. Reads drive the unread marks; all three are removed with the account.
alter table public.notices
    add column if not exists admin_only boolean not null default false;

create table if not exists public.notice_reads (
    id bigserial primary key,
    notice_id bigint not null references public.notices(id) on delete cascade,
    reader_email varchar(320) not null,
    read_at timestamptz not null default now(),
    constraint uq_notice_reads_reader unique (notice_id, reader_email)
);

create index if not exists idx_notice_reads_reader
    on public.notice_reads (reader_email);

create table if not exists public.notice_likes (
    id bigserial primary key,
    notice_id bigint not null references public.notices(id) on delete cascade,
    liker_email varchar(320) not null,
    created_at timestamptz not null default now(),
    constraint uq_notice_likes_liker unique (notice_id, liker_email)
);

create index if not exists idx_notice_likes_liker
    on public.notice_likes (liker_email);

create table if not exists public.notice_comments (
    id bigserial primary key,
    notice_id bigint not null references public.notices(id) on delete cascade,
    author_email varchar(320) not null,
    content varchar(500) not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_notice_comments_notice
    on public.notice_comments (notice_id, id);

create index if not exists idx_notice_comments_author
    on public.notice_comments (author_email);

alter table if exists public.notice_reads enable row level security;
drop policy if exists no_client_access on public.notice_reads;
create policy no_client_access on public.notice_reads as permissive for all to public using (false) with check (false);

alter table if exists public.notice_likes enable row level security;
drop policy if exists no_client_access on public.notice_likes;
create policy no_client_access on public.notice_likes as permissive for all to public using (false) with check (false);

alter table if exists public.notice_comments enable row level security;
drop policy if exists no_client_access on public.notice_comments;
create policy no_client_access on public.notice_comments as permissive for all to public using (false) with check (false);
