create table if not exists youtube_videos
(
    video_id       text      not null unique,
    title          text      not null,
    description    text      not null,
    published_at   timestamp not null,
    thumbnail_url  text      not null,
    tags           text[],
    category_id    int,
    view_count     int,
    like_count     int,
    favorite_count int,
    comment_count  int
);

create table if not exists promoted_youtube_videos
(
    video_id    text      not null unique,
    promoted_at timestamp null
);