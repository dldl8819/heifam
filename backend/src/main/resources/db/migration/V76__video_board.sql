-- A third member board, 추천영상: a post there is a YouTube video with a title and, if the writer
-- likes, a few words, read, liked and commented on by members as on the free board.
-- Only the video's id is kept (eleven letters, digits, '-' and '_'), never the link as typed: the
-- page builds the player's address from the id alone. A post on any other board has none.
-- Adds one column and widens the board check.
alter table public.board_posts
    add column if not exists video_id varchar(11);

alter table public.board_posts
    drop constraint if exists ck_board_posts_board;

alter table public.board_posts
    add constraint ck_board_posts_board check (board in ('FREE', 'ANONYMOUS', 'VIDEO'));

alter table public.board_posts
    drop constraint if exists ck_board_posts_video;

alter table public.board_posts
    add constraint ck_board_posts_video check (
        -- "is not null" spelled out: a check whose answer is unknown (a null compared) lets the row in.
        (board = 'VIDEO' and video_id is not null and video_id ~ '^[A-Za-z0-9_-]{11}$')
        or (board <> 'VIDEO' and video_id is null)
    );
