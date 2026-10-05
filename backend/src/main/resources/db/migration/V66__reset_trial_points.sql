-- Points open to every member now. As announced on the points page during the admins' trial,
-- the points earned in that trial start over: every account and its ledger go (the transactions
-- follow the accounts by on delete cascade). Predictions, prize events and their ledger expenses
-- stay as they are. Touches only the point tables.
delete from public.point_accounts;
