-- Images shown inside a notice's text. An image is uploaded first and belongs to no notice until
-- the notice whose text names it is saved; one still unplaced a day later is removed at the next
-- upload. Images go with their notice. Nothing here says who uploaded one, so there is nothing to
-- clear with an account. Adds this table only.
create table if not exists public.notice_images (
    id bigserial primary key,
    group_id bigint not null references public.groups(id) on delete cascade,
    notice_id bigint references public.notices(id) on delete cascade,
    content_type varchar(40) not null,
    byte_size integer not null,
    data bytea not null,
    created_at timestamptz not null default now()
);

-- Image files are compressed already; trying again on every write only costs time.
alter table public.notice_images alter column data set storage external;

create index if not exists idx_notice_images_notice
    on public.notice_images (notice_id);

alter table if exists public.notice_images enable row level security;
drop policy if exists no_client_access on public.notice_images;
create policy no_client_access on public.notice_images as permissive for all to public using (false) with check (false);
