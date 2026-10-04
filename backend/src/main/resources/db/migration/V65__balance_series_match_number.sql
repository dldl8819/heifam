-- Which match of its multi-balance a series plays (1, 2, ...), so the board keeps the teams the
-- multi-balance named: match 1 is teams 1 and 2, match 2 teams 3 and 4. Series started before
-- this have none. A new nullable column, so no rewrite of the table.
alter table public.balance_series
    add column if not exists match_number integer;
