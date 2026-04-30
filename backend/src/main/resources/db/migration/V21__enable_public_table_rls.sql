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
