alter table players
    add column if not exists active boolean not null default true;

update players
set active = true
where active is null;
