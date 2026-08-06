alter table public.players
    add column if not exists lifecycle_status varchar(20) not null default 'ACTIVE',
    add column if not exists identity_retained_until timestamptz;

update public.players
set lifecycle_status = case
        when anonymized_at is not null then 'ANONYMIZED'
        when active = false then 'INACTIVE'
        else 'ACTIVE'
    end,
    identity_retained_until = case
        when active = false and anonymized_at is null
            then least(
                coalesce(chat_left_at, now()) + interval '5 years',
                now() + interval '5 years'
            )
        else null
    end;

alter table public.players
    drop constraint if exists ck_players_lifecycle_status;

alter table public.players
    add constraint ck_players_lifecycle_status
    check (lifecycle_status in ('ACTIVE', 'INACTIVE', 'WITHDRAWN', 'ANONYMIZED'))
    not valid;

alter table public.players
    validate constraint ck_players_lifecycle_status;

create index if not exists idx_players_identity_retention_expiry
    on public.players (identity_retained_until, id)
    where active = false
      and anonymized_at is null
      and identity_retained_until is not null;
