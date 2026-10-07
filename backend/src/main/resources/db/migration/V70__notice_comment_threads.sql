-- Replies, edits and likes on notice comments.
-- A reply names the comment it answers (parent_id); replies are one level deep. edited_at is set
-- when the writer changes the text. A comment deleted while it has replies is kept as an emptied
-- place (deleted_at set, text and writer cleared) so the replies keep their thread. A like is one
-- row per person and comment and is removed with the account, like likes on notices.
-- Existing comments get no parent and no marks. Adds three columns and one table.
alter table public.notice_comments
    add column if not exists parent_id bigint references public.notice_comments(id) on delete cascade,
    add column if not exists edited_at timestamptz,
    add column if not exists deleted_at timestamptz;

create index if not exists idx_notice_comments_parent
    on public.notice_comments (parent_id);

create table if not exists public.notice_comment_likes (
    id bigserial primary key,
    comment_id bigint not null references public.notice_comments(id) on delete cascade,
    liker_email varchar(320) not null,
    created_at timestamptz not null default now(),
    constraint uq_notice_comment_likes_liker unique (comment_id, liker_email)
);

create index if not exists idx_notice_comment_likes_liker
    on public.notice_comment_likes (liker_email);

alter table if exists public.notice_comment_likes enable row level security;
drop policy if exists no_client_access on public.notice_comment_likes;
create policy no_client_access on public.notice_comment_likes as permissive for all to public using (false) with check (false);
