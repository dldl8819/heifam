create table if not exists public.pending_auth_user_deletions (
    auth_user_id uuid primary key,
    created_at timestamptz not null default now(),
    last_attempt_at timestamptz
);

create index if not exists idx_pending_auth_user_deletions_retry
    on public.pending_auth_user_deletions (last_attempt_at, created_at);

alter table public.pending_auth_user_deletions enable row level security;

drop policy if exists no_client_access on public.pending_auth_user_deletions;
create policy no_client_access
    on public.pending_auth_user_deletions
    as permissive
    for all
    to public
    using (false)
    with check (false);

revoke all on table public.pending_auth_user_deletions from public, anon, authenticated;

create temporary table inactive_player_privacy_targets
on commit drop
as
select
    player.id as player_id,
    player.group_id,
    player.auth_user_id,
    nullif(lower(btrim(account.email)), '') as normalized_email,
    player.auth_user_id is not null
        and not exists (
            select 1
            from public.players active_player
            where active_player.auth_user_id = player.auth_user_id
              and active_player.id <> player.id
              and active_player.active = true
              and active_player.anonymized_at is null
        ) as sole_linked_account
from public.players player
left join public.users account
    on account.id = player.auth_user_id
where player.active = false;

insert into public.pending_auth_user_deletions (auth_user_id, created_at, last_attempt_at)
select distinct auth_user_id, now(), null
from inactive_player_privacy_targets
where sole_linked_account = true
  and auth_user_id is not null
on conflict (auth_user_id) do nothing;

update public.matches match_record
set result_recorded_by_email = null,
    result_recorded_by_nickname = U&'\D0C8\D1F4\D55C \D68C\C6D0'
where exists (
    select 1
    from inactive_player_privacy_targets target
    where target.normalized_email is not null
      and lower(btrim(match_record.result_recorded_by_email)) = target.normalized_email
      and (
          target.sole_linked_account = true
          or match_record.group_id = target.group_id
      )
);

update public.operation_audit_logs audit_log
set actor_email = null,
    actor_nickname = U&'\D0C8\D1F4\D55C \D68C\C6D0'
where exists (
    select 1
    from inactive_player_privacy_targets target
    where target.normalized_email is not null
      and lower(btrim(audit_log.actor_email)) = target.normalized_email
      and (
          target.sole_linked_account = true
          or audit_log.group_id = target.group_id
      )
);

update public.operation_audit_logs
set target_id = null,
    target_label = U&'\D0C8\D1F4\D55C \D68C\C6D0',
    details = null
where target_type = 'PLAYER'
  and target_id in (
      select player_id
      from inactive_player_privacy_targets
  );

update public.managed_admin_emails
set created_by_email = null
where lower(btrim(created_by_email)) in (
    select normalized_email
    from inactive_player_privacy_targets
    where sole_linked_account = true
      and normalized_email is not null
);

update public.allowed_user_emails
set created_by_email = null
where lower(btrim(created_by_email)) in (
    select normalized_email
    from inactive_player_privacy_targets
    where sole_linked_account = true
      and normalized_email is not null
);

update public.admin_mmr_access_emails
set created_by_email = null
where lower(btrim(created_by_email)) in (
    select normalized_email
    from inactive_player_privacy_targets
    where sole_linked_account = true
      and normalized_email is not null
);

delete from public.admin_mmr_access_emails
where normalized_email in (
    select normalized_email
    from inactive_player_privacy_targets
    where sole_linked_account = true
      and normalized_email is not null
);

delete from public.managed_admin_emails
where normalized_email in (
    select normalized_email
    from inactive_player_privacy_targets
    where sole_linked_account = true
      and normalized_email is not null
);

delete from public.allowed_user_emails
where normalized_email in (
    select normalized_email
    from inactive_player_privacy_targets
    where sole_linked_account = true
      and normalized_email is not null
);

delete from public.user_race_preferences
where normalized_email in (
    select normalized_email
    from inactive_player_privacy_targets
    where sole_linked_account = true
      and normalized_email is not null
);

delete from public.users
where id in (
    select auth_user_id
    from inactive_player_privacy_targets
    where sole_linked_account = true
      and auth_user_id is not null
);

update public.players
set auth_user_id = null,
    nickname = U&'\D0C8\D1F4\D55C \D68C\C6D0',
    note = null,
    chat_left_at = null,
    chat_left_reason = null,
    chat_rejoined_at = null,
    tier_change_acknowledged_tier = null,
    tier_change_acknowledged_at = null,
    anonymized_at = coalesce(anonymized_at, now())
where id in (
    select player_id
    from inactive_player_privacy_targets
);

-- Runtime deactivation refuses identities still present in environment-based access lists.
-- Existing environment-based grants are outside the database and must be removed by operations.
