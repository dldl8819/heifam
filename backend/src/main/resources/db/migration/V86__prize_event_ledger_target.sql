-- A prize event's ledger expense now names its winner in target, as hand-written expenses name who
-- was paid. Fill the expenses booked before, from the winner's name kept on the event; a winner who
-- has withdrawn has no name there, and their expense stays without one.
update public.ledger_expense_entries e
set target = w.nickname
from public.prize_event_winners w
where w.ledger_expense_id = e.id
  and w.nickname is not null
  and (e.target is null or btrim(e.target) = '');
