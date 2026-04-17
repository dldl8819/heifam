create table if not exists public.users (
    id uuid primary key references auth.users(id),
    email text unique,
    name text,
    avatar_url text,
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

alter table if exists public.users enable row level security;

drop policy if exists "Users can view own data" on public.users;
create policy "Users can view own data"
    on public.users
    for select
    to authenticated
    using ((select auth.uid()) = id);

drop policy if exists "Users can insert own data" on public.users;
create policy "Users can insert own data"
    on public.users
    for insert
    to authenticated
    with check ((select auth.uid()) = id);

drop policy if exists "Users can update own data" on public.users;
create policy "Users can update own data"
    on public.users
    for update
    to authenticated
    using ((select auth.uid()) = id)
    with check ((select auth.uid()) = id);

do $$
declare
    protected_table text;
    protected_tables text[] := array[
        'allowed_user_emails',
        'app_bootstrap',
        'captain_draft_entries',
        'captain_draft_participants',
        'captain_drafts',
        'groups',
        'managed_admin_emails',
        'match_participants',
        'matches',
        'mmr_history',
        'players',
        'user_race_preferences'
    ];
begin
    foreach protected_table in array protected_tables loop
        execute format('alter table if exists public.%I enable row level security', protected_table);
        execute format('drop policy if exists no_client_access on public.%I', protected_table);
        execute format(
            'create policy no_client_access on public.%I as permissive for all to public using (false) with check (false)',
            protected_table
        );
    end loop;
end $$;

create index if not exists idx_captain_draft_entries_home_player_id
    on public.captain_draft_entries (home_player_id);

create index if not exists idx_captain_draft_entries_away_player_id
    on public.captain_draft_entries (away_player_id);

create index if not exists idx_captain_drafts_home_captain_player_id
    on public.captain_drafts (home_captain_player_id);

create index if not exists idx_captain_drafts_away_captain_player_id
    on public.captain_drafts (away_captain_player_id);

create index if not exists idx_match_participants_player_id
    on public.match_participants (player_id);

create index if not exists idx_mmr_history_match_id
    on public.mmr_history (match_id);

create index if not exists idx_mmr_history_player_id
    on public.mmr_history (player_id);

create index if not exists idx_players_group_id
    on public.players (group_id);

alter table if exists public.captain_draft_entries
    drop constraint if exists uklo8ptlh0frm36lcf6it4wh8hb;

alter table if exists public.captain_draft_participants
    drop constraint if exists ukku8xgwrymlro9vwbrxjgycrit;

drop index if exists public.idx_captain_draft_entries_draft_id;
drop index if exists public.idx_captain_draft_participants_draft_id;
drop index if exists public.flyway_schema_history_s_idx;
