-- Who set a match up (pressed "경기 확정", started the series, or entered the result that set up
-- the next series game), so the predictions page can show whom a match is waiting on. Matches set
-- up before this have none. Cleared with the account, like the result recorder. Adds this column only.
alter table public.matches
    add column if not exists created_by_email varchar(320);
