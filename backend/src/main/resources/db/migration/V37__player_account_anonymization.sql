alter table public.players
    add column if not exists auth_user_id uuid,
    add column if not exists anonymized_at timestamptz;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_players_auth_user_id'
            and conrelid = 'public.players'::regclass
    ) then
        alter table public.players
            add constraint fk_players_auth_user_id
            foreign key (auth_user_id)
            references auth.users(id)
            on delete set null;
    end if;
end
$$;

create index if not exists idx_players_active_auth_user_id
    on public.players (auth_user_id)
    where auth_user_id is not null and anonymized_at is null;

with ranked_profiles as (
    select normalized_email, nickname, 1 as priority
    from public.managed_admin_emails
    where nullif(btrim(normalized_email), '') is not null
      and nullif(btrim(nickname), '') is not null
    union all
    select normalized_email, nickname, 2 as priority
    from public.allowed_user_emails
    where nullif(btrim(normalized_email), '') is not null
      and nullif(btrim(nickname), '') is not null
),
selected_profiles as (
    select distinct on (lower(btrim(normalized_email)))
        lower(btrim(normalized_email)) as normalized_email,
        nickname
    from ranked_profiles
    order by lower(btrim(normalized_email)), priority
),
unique_profiles as (
    select
        lower(btrim(nickname)) as nickname_key,
        min(normalized_email) as normalized_email
    from selected_profiles
    group by lower(btrim(nickname))
    having count(distinct normalized_email) = 1
),
auth_accounts as (
    select id, lower(btrim(email)) as normalized_email
    from public.users
    where nullif(btrim(email), '') is not null
)
update public.players player
set auth_user_id = auth_account.id
from unique_profiles profile
join auth_accounts auth_account
    on auth_account.normalized_email = profile.normalized_email
where player.auth_user_id is null
  and player.anonymized_at is null
  and lower(btrim(player.nickname)) = profile.nickname_key;
