-- Member boards beside the notices. FREE is read and written by every member. ANONYMOUS is a
-- suggestion box: any member writes, only admins and the writer read. Both tables keep who wrote
-- (author_email): on the free board it is shown as a nickname; on the anonymous board the API
-- never sends it out, to admins either, and it serves only to let the writer find, change and
-- remove their own post and read the answers on it.
-- A view is one row per person and post, so "seen by" counts people, not page loads.
-- Everything a person left here is removed with their account. Adds four tables.
create table if not exists public.board_posts (
    id bigserial primary key,
    group_id bigint not null references public.groups(id) on delete cascade,
    board varchar(20) not null,
    title varchar(200) not null,
    content text not null,
    author_email varchar(320) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_board_posts_board check (board in ('FREE', 'ANONYMOUS'))
);

create index if not exists idx_board_posts_board_created
    on public.board_posts (group_id, board, created_at desc, id desc);

create index if not exists idx_board_posts_author
    on public.board_posts (author_email);

create table if not exists public.board_comments (
    id bigserial primary key,
    post_id bigint not null references public.board_posts(id) on delete cascade,
    author_email varchar(320) not null,
    content varchar(500) not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_board_comments_post
    on public.board_comments (post_id, id);

create index if not exists idx_board_comments_author
    on public.board_comments (author_email);

create table if not exists public.board_post_likes (
    id bigserial primary key,
    post_id bigint not null references public.board_posts(id) on delete cascade,
    liker_email varchar(320) not null,
    created_at timestamptz not null default now(),
    constraint uq_board_post_likes_liker unique (post_id, liker_email)
);

create index if not exists idx_board_post_likes_liker
    on public.board_post_likes (liker_email);

create table if not exists public.board_post_views (
    id bigserial primary key,
    post_id bigint not null references public.board_posts(id) on delete cascade,
    viewer_email varchar(320) not null,
    viewed_at timestamptz not null default now(),
    constraint uq_board_post_views_viewer unique (post_id, viewer_email)
);

create index if not exists idx_board_post_views_viewer
    on public.board_post_views (viewer_email);

alter table if exists public.board_posts enable row level security;
drop policy if exists no_client_access on public.board_posts;
create policy no_client_access on public.board_posts as permissive for all to public using (false) with check (false);

alter table if exists public.board_comments enable row level security;
drop policy if exists no_client_access on public.board_comments;
create policy no_client_access on public.board_comments as permissive for all to public using (false) with check (false);

alter table if exists public.board_post_likes enable row level security;
drop policy if exists no_client_access on public.board_post_likes;
create policy no_client_access on public.board_post_likes as permissive for all to public using (false) with check (false);

alter table if exists public.board_post_views enable row level security;
drop policy if exists no_client_access on public.board_post_views;
create policy no_client_access on public.board_post_views as permissive for all to public using (false) with check (false);
