do $$
declare
    protected_table text;
    protected_tables text[] := array[
        'admin_mmr_access_emails',
        'operation_audit_logs'
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
