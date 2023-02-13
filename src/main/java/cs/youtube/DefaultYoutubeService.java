package cs.youtube;

import cs.youtube.client.Video;
import cs.youtube.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.util.Assert;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
class DefaultYoutubeService implements YoububeAggregate {

	private final ApplicationEventPublisher publisher;

	private final YoutubeClient client;

	private final String channelId;

	private final DatabaseClient databaseClient;

	private final String unpromotedQuerySql = """
			    select  yt.* from
			        promoted_youtube_videos pyt ,
			        youtube_videos yt
			    where
			        yt.video_id = pyt.video_id and
			        pyt.promoted_at is null;
			""";

	private final String youtubeVideosUpsertSql = """
			insert into youtube_videos (
			    video_id     ,
			    title        ,
			    description  ,
			    published_at ,
			    thumbnail_url,
			    category_id  ,
			    view_count   ,
			    like_count   ,
			    favorite_count,
			    comment_count ,
			    tags,
			    fresh
			)
			values (
			    :video_id     ,
			    :title        ,
			    :description  ,
			    :published_at ,
			    :thumbnail_url,
			    :category_id  ,
			    :view_count   ,
			    :like_count   ,
			    :favorite_count,
			    :comment_count ,
			    :tags,
			    true
			)
			on conflict (video_id) do update set
			        title = excluded.title,
			        description = excluded.description,
			        published_at = excluded.published_at,
			        thumbnail_url = excluded.thumbnail_url,
			        category_id = excluded.category_id,
			        view_count = excluded.view_count,
			        like_count = excluded.like_count,
			        favorite_count = excluded.favorite_count,
			        comment_count = excluded.comment_count,
			        tags = excluded.tags ,
			        fresh = true
			;
			""";

	private final String promotedYoutubeVideosUpsertSql = """
			insert into promoted_youtube_videos( video_id )
			select video_id from youtube_videos
			on conflict (video_id) do nothing
			""";

	private final List<Video> videos = new CopyOnWriteArrayList<>();

	@Override
	public List<Video> videos() {
		return this.videos;
	}

	@Override
	public void refresh() {

		var mapVideoFunction = (Function<Map<String, Object>, Video>) row -> new Video(readColumn(row, "video_id"), //
				readColumn(row, "title"), //
				readColumn(row, "description"), //
				buildDateFromLocalDateTime(readColumn(row, "published_at")), //
				buildUrlFromString(readColumn(row, "thumbnail_url")), //
				buildListFromArray(readColumn(row, "tags")), //
				readColumn(row, "category_id"), //
				readColumn(row, "view_count"), //
				readColumn(row, "like_count"), //
				readColumn(row, "favorite_count"), //
				readColumn(row, "comment_count"), //
				this.channelId);
		var collection = this.databaseClient //
				.sql("update youtube_videos set fresh = false").fetch().rowsUpdated()
				.thenMany(this.client.getChannelById(this.channelId))//
				.flatMap(channel -> this.client.getAllVideosByChannel(channel.channelId()))//
				.flatMap(video -> this.databaseClient//
						.sql(this.youtubeVideosUpsertSql)//
						.bind("video_id", video.videoId())//
						.bind("title", video.title())//
						.bind("description", video.description())//
						.bind("published_at", video.publishedAt())//
						.bind("thumbnail_url", video.standardThumbnail().toExternalForm())//
						.bind("category_id", video.categoryId())//
						.bind("view_count", video.viewCount())//
						.bind("like_count", video.likeCount())//
						.bind("favorite_count", video.favoriteCount())//
						.bind("comment_count", video.commentCount())//
						.bind("tags", video.tags().toArray(new String[0]))//
						.fetch()//
						.rowsUpdated()//
				) //
				.thenMany(this.databaseClient.sql(this.promotedYoutubeVideosUpsertSql).fetch().rowsUpdated())
				.thenMany(this.databaseClient.sql(this.unpromotedQuerySql).fetch().all().map(mapVideoFunction))//
				.doOnNext(video -> this.publisher.publishEvent(new YoutubeVideoCreatedEvent(video)))
				.doOnError(throwable -> log.error(throwable.getMessage()))//
				.thenMany(this.databaseClient.sql("select * from youtube_videos where fresh = true ").fetch().all()
						.map(mapVideoFunction)) //
				.toStream()//
				.collect(Collectors.toSet());

		synchronized (this.videos) {
			this.videos.clear();
			this.videos.addAll(collection);
			this.videos.sort(Comparator.comparing(Video::publishedAt).reversed());
			log.info("there are {} {} videos.", this.videos.size(), Video.class.getSimpleName());
		}

	}

	@SneakyThrows
	private static URL buildUrlFromString(String urlString) {
		return new URL(urlString);
	}

	@SuppressWarnings("unchecked")
	private static @NonNull <T> T readColumn(Map<String, Object> row, String key) {
		if (row.containsKey(key))
			return (T) row.getOrDefault(key, null);
		throw new IllegalArgumentException("we should never reach this point!");
	}

	private static Date buildDateFromLocalDateTime(@NonNull LocalDateTime localDateTime) {
		Assert.notNull(localDateTime, "the " + LocalDateTime.class.getName() + " must not be null");
		return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
	}

	private static List<String> buildListFromArray(String[] tags) {
		if (tags != null)
			return Arrays.asList(tags);
		return List.of();
	}

}
