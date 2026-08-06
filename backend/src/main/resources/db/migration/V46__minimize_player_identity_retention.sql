alter table public.players
    add column if not exists retention_subject_hash varchar(64),
    add column if not exists version bigint not null default 0;

-- Valid active rows never need retention-only linkage metadata. Clearing these
-- fields is safer than anonymizing an otherwise valid active player if legacy
-- writes left stale values behind.
update public.players
set identity_retained_until = null,
    retention_subject_hash = null
where lifecycle_status = 'ACTIVE'
  and active = true
  and anonymized_at is null;

create temporary table player_identity_minimization_targets
on commit drop
as
select player.id
from public.players player
where (
      player.lifecycle_status = 'INACTIVE'
      and (
          player.active = true
          or player.anonymized_at is not null
          or player.chat_left_at is null
          or player.chat_left_at > now()
          or player.identity_retained_until is null
          or player.identity_retained_until <= player.chat_left_at
          or nullif(btrim(player.nickname), '') is null
          or player.nickname = U&'\D0C8\D1F4\D55C \D68C\C6D0'
          or least(
              player.identity_retained_until,
              coalesce(player.chat_left_at, now()) + interval '1 year',
              now() + interval '1 year'
          ) <= now()
      )
  )
  or (
      player.lifecycle_status <> 'INACTIVE'
      and (
          player.active = false
          or player.anonymized_at is not null
          or player.lifecycle_status <> 'ACTIVE'
      )
  );

update public.operation_audit_logs audit_log
set target_id = null,
    target_label = U&'\D0C8\D1F4\D55C \D68C\C6D0',
    details = null
where audit_log.target_type = 'PLAYER'
  and exists (
      select 1
      from player_identity_minimization_targets target
      where target.id = audit_log.target_id
  );

update public.players player
set auth_user_id = null,
    nickname = U&'\D0C8\D1F4\D55C \D68C\C6D0',
    note = null,
    active = false,
    chat_left_at = null,
    chat_left_reason = null,
    chat_rejoined_at = null,
    tier_change_acknowledged_tier = null,
    tier_change_acknowledged_at = null,
    anonymized_at = coalesce(player.anonymized_at, now()),
    lifecycle_status = 'ANONYMIZED',
    identity_retained_until = null,
    retention_subject_hash = null
where exists (
    select 1
    from player_identity_minimization_targets target
    where target.id = player.id
);

update public.players player
set auth_user_id = null,
    retention_subject_hash = null,
    note = null,
    chat_left_at = coalesce(player.chat_left_at, now()),
    chat_rejoined_at = null,
    tier_change_acknowledged_tier = null,
    tier_change_acknowledged_at = null,
    chat_left_reason = case
        when player.chat_left_reason in (
            U&'\C7A5\AE30 \BBF8\CC38\C5EC',
            U&'\BCF8\C778 \C694\CCAD',
            U&'\C6B4\C601 \C815\CC45',
            U&'\AE30\D0C0'
        ) then player.chat_left_reason
        else U&'\AE30\D0C0'
    end,
    identity_retained_until = least(
        player.identity_retained_until,
        coalesce(player.chat_left_at, now()) + interval '1 year',
        now() + interval '1 year'
    )
where player.active = false
  and player.anonymized_at is null
  and player.lifecycle_status = 'INACTIVE'
  and player.identity_retained_until > now();

alter table public.players
    drop constraint if exists ck_players_inactive_identity_retention;

alter table public.players
    add constraint ck_players_inactive_identity_retention
    check (
        lifecycle_status <> 'INACTIVE'
        or (
            active = false
            and anonymized_at is null
            and auth_user_id is null
            and nullif(btrim(nickname), '') is not null
            and nickname <> U&'\D0C8\D1F4\D55C \D68C\C6D0'
            and chat_left_at is not null
            and chat_left_reason is not null
            and chat_left_reason in (
                U&'\C7A5\AE30 \BBF8\CC38\C5EC',
                U&'\BCF8\C778 \C694\CCAD',
                U&'\C6B4\C601 \C815\CC45',
                U&'\AE30\D0C0'
            )
            and chat_rejoined_at is null
            and identity_retained_until is not null
            and identity_retained_until > chat_left_at
            and identity_retained_until <= chat_left_at + interval '1 year'
            and note is null
            and tier_change_acknowledged_tier is null
            and tier_change_acknowledged_at is null
        )
    )
    not valid;

alter table public.players
    validate constraint ck_players_inactive_identity_retention;

create index if not exists idx_players_retention_subject_hash
    on public.players (retention_subject_hash)
    where retention_subject_hash is not null;

alter table public.players
    drop constraint if exists ck_players_retention_subject_hash_scope;

alter table public.players
    add constraint ck_players_retention_subject_hash_scope
    check (
        retention_subject_hash is null
        or (
            lifecycle_status = 'INACTIVE'
            and active = false
            and anonymized_at is null
            and identity_retained_until is not null
            and retention_subject_hash ~ '^[0-9a-f]{64}$'
        )
    )
    not valid;

alter table public.players
    validate constraint ck_players_retention_subject_hash_scope;

alter table public.players
    drop constraint if exists ck_players_lifecycle_identity_consistency;

alter table public.players
    add constraint ck_players_lifecycle_identity_consistency
    check (
        (
            lifecycle_status = 'ACTIVE'
            and active = true
            and anonymized_at is null
            and identity_retained_until is null
            and retention_subject_hash is null
        )
        or lifecycle_status = 'INACTIVE'
        or (
            lifecycle_status = 'ANONYMIZED'
            and active = false
            and anonymized_at is not null
            and auth_user_id is null
            and nickname = U&'\D0C8\D1F4\D55C \D68C\C6D0'
            and note is null
            and chat_left_at is null
            and chat_left_reason is null
            and chat_rejoined_at is null
            and tier_change_acknowledged_tier is null
            and tier_change_acknowledged_at is null
            and identity_retained_until is null
            and retention_subject_hash is null
        )
    )
    not valid;

alter table public.players
    validate constraint ck_players_lifecycle_identity_consistency;
