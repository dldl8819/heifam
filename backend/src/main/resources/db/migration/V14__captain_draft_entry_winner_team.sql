alter table captain_draft_entries
    add column if not exists winner_team varchar(10);
