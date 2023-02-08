package cs.youtube;

import cs.youtube.client.Video;
import cs.youtube.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

@EnableConfigurationProperties(YoutubeProperties.class)
@SpringBootApplication
public class YoutubeApplication {

	public static void main(String[] args) {
		SpringApplication.run(YoutubeApplication.class, args);
	}

	@Bean
	WebClient client(WebClient.Builder builder) {
		return builder.build();
	}

	@Bean
	YoutubeService youtubeService(YoutubeClient client, YoutubeProperties properties, DatabaseClient databaseClient) {
		return new YoutubeService(client, properties.channelId(), databaseClient);
	}

}

@Slf4j
@RequiredArgsConstructor
class YoutubeService {

	private final YoutubeClient client;

	private final String channelId;

	private final DatabaseClient databaseClient;

	private final String insertSql = """
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
			    tags
			)
			values(
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
			    :tags
			) ;
			""";

	@EventListener({ ApplicationReadyEvent.class, YoutubeChannelUpdatedEvent.class })
	public void refresh() {
		var fluxOfVideos = this.client//
				.getChannelById(this.channelId)//
				.flatMapMany(channel -> this.client.getAllVideosByChannel(channel.channelId()))//
				.flatMap(video -> this.databaseClient//
						.sql(this.insertSql)//
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
				);
		fluxOfVideos//
				.doOnError(throwable -> log.error(throwable.getMessage()))//
				.thenMany(all())//
				.subscribe(video -> log.info(video.toString()));

	}

	@SneakyThrows
	private static URL urlFrom(String urlString) {
		return new URL(urlString);
	}

	@SuppressWarnings("unchecked")
	private <T> T col(Map<String, Object> row, String key) {
		if (row.containsKey(key))
			return (T) row.getOrDefault(key, null);
		return null;
	}

	private Date dateFromLocalDateTime(@NonNull LocalDateTime localDateTime) {
		Assert.notNull(localDateTime, "the " + LocalDateTime.class.getName() + " must not be null");
		return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
	}

	private List<String> listFromArray(String[] tags) {
		if (tags != null)
			return Arrays.asList(tags);
		return List.of();
	}

	private Flux<Video> all() {
		return this.databaseClient.sql("select * from  youtube_videos").fetch().all()
				.map(row -> new Video(col(row, "video_id"), col(row, "title"), col(row, "description"),
						dateFromLocalDateTime(col(row, "published_at")), urlFrom(col(row, "thumbnail_url")),
						listFromArray(col(row, "tags")), col(row, "category_id"), col(row, "view_count"),
						col(row, "like_count"), col(row, "favorite_count"), col(row, "comment_count"),
						col(row, "channel_id")));
	}

}
