-- Notifications: one row per event (a notice published, predictions opened), shown to everyone
-- in its audience (MEMBERS with service access, or ADMINS). Each account keeps how far it has
-- read, and may register browsers or installed apps for Web Push. Bodies hold no player names.
create table if not exists public.notifications (
    id bigserial primary key,
    group_id bigint not null references public.groups(id),
    kind varchar(30) not null,
    target_id bigint,
    audience varchar(20) not null,
    title varchar(120) not null,
    body varchar(300),
    link varchar(300),
    created_at timestamptz not null default now()
);

create index if not exists idx_notifications_group_audience
    on public.notifications (group_id, audience, id desc);

create index if not exists idx_notifications_target
    on public.notifications (kind, target_id);

create index if not exists idx_notifications_created
    on public.notifications (created_at);

create table if not exists public.notification_cursors (
    email varchar(320) primary key,
    last_read_id bigint not null default 0,
    updated_at timestamptz not null default now()
);

create table if not exists public.push_subscriptions (
    id bigserial primary key,
    email varchar(320) not null,
    endpoint varchar(1000) not null,
    p256dh varchar(200) not null,
    auth varchar(100) not null,
    created_at timestamptz not null default now(),
    constraint uq_push_subscriptions_endpoint unique (endpoint)
);

create index if not exists idx_push_subscriptions_email
    on public.push_subscriptions (email);

alter table if exists public.notifications enable row level security;
drop policy if exists no_client_access on public.notifications;
create policy no_client_access on public.notifications as permissive for all to public using (false) with check (false);

alter table if exists public.notification_cursors enable row level security;
drop policy if exists no_client_access on public.notification_cursors;
create policy no_client_access on public.notification_cursors as permissive for all to public using (false) with check (false);

alter table if exists public.push_subscriptions enable row level security;
drop policy if exists no_client_access on public.push_subscriptions;
create policy no_client_access on public.push_subscriptions as permissive for all to public using (false) with check (false);
