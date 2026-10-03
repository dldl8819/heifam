-- Signed-in clients write their own public.users row, but nothing tied its email to the account,
-- so a member could store someone else's address there. The backend reads that email when it
-- cleans up an inactive member's account, so it must be the email the account signed in with.
drop policy if exists "Users can insert own data" on public.users;
create policy "Users can insert own data"
    on public.users
    for insert
    to authenticated
    with check (
        (select auth.uid()) = id
        and lower(email) = lower((select auth.jwt() ->> 'email'))
    );

drop policy if exists "Users can update own data" on public.users;
create policy "Users can update own data"
    on public.users
    for update
    to authenticated
    using ((select auth.uid()) = id)
    with check (
        (select auth.uid()) = id
        and lower(email) = lower((select auth.jwt() ->> 'email'))
    );

-- Flyway keeps its history in public, where Supabase lets client roles reach tables by default.
-- Clients never need it. RLS is left alone because Flyway writes to this table while migrating;
-- revoking takes no lock on it, and a failure only warns rather than stopping the deploy.
do $$
declare
    client_role text;
begin
    if to_regclass('public.flyway_schema_history') is null then
        return;
    end if;
    foreach client_role in array array['anon', 'authenticated'] loop
        if exists (select 1 from pg_roles where rolname = client_role) then
            execute format('revoke all on table public.flyway_schema_history from %I', client_role);
        end if;
    end loop;
exception
    when insufficient_privilege then
        raise warning 'Could not revoke client access to flyway_schema_history';
end $$;
