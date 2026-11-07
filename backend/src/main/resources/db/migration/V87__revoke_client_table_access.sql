-- The site reaches these tables only through the backend, which owns them. Supabase grants its
-- client roles (anon, and authenticated: any signed-in account) every right on each table made in
-- public, and only row level security stood between them and the rows. public.users let any
-- signed-in account write its own row, a Google account with no access to the site included, with
-- names and avatar addresses of any length. The client roles now lose every right on public's
-- tables and sequences, and on those later migrations make there (the migrations' role is the one
-- whose default grants Supabase set). Row level security and its policies stay as a second wall.
-- GRANT and REVOKE take no lock that waits on Flyway's hold on flyway_schema_history.
do $$
declare
    client_role text;
begin
    foreach client_role in array array['anon', 'authenticated'] loop
        if exists (select 1 from pg_roles where rolname = client_role) then
            execute format('revoke all on all tables in schema public from %I', client_role);
            execute format('revoke all on all sequences in schema public from %I', client_role);
            execute format('alter default privileges in schema public revoke all on tables from %I', client_role);
            execute format('alter default privileges in schema public revoke all on sequences from %I', client_role);
        end if;
    end loop;
end $$;
