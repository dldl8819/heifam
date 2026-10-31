-- A vote on a notice can let a voter choose more than one option (중복 투표), when its writer says so.
-- A person then has a vote on each option they chose instead of one vote per notice, so the
-- one-a-notice key gives way to one a notice and option. Every vote so far keeps one choice.
alter table public.notices
    add column if not exists vote_multiple boolean not null default false;

alter table public.notice_votes
    drop constraint if exists uq_notice_votes_voter;

alter table public.notice_votes
    add constraint uq_notice_votes_voter_option unique (notice_id, voter_email, option_id);
