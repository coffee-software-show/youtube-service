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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@EnableConfigurationProperties(YoutubeProperties.class)
@SpringBootApplication
public class YoutubeApplication {

	public static void main(String[] args) {
		SpringApplication.run(YoutubeApplication.class, args);
	}

	@Bean
	WebClient webClient(WebClient.Builder builder) {
		return builder.build();
	}

	@Bean
	DefaultYoutubeService youtubeService(ApplicationEventPublisher applicationEventPublisher, YoutubeClient client,
			YoutubeProperties properties, DatabaseClient databaseClient) {
		return new DefaultYoutubeService(applicationEventPublisher, client, properties.channelId(), databaseClient);
	}

}

// todo
@RequiredArgsConstructor
class PubsubHubbubClient {

	private final WebClient http;

	private final URL hubUrl;

	public enum Verify {

		ASYNC, SYNC

	}

	public enum Mode {

		SUBSCRIBE, UNSUBSCRIBE

	}

	Mono<ResponseEntity<Void>> unsubscribe(URL topicUrl, URL callbackUrl, Verify verify, long leaseInSeconds,
			String verifyToken) {
		return this.changeSubscription(leaseInSeconds, verify, verifyToken, callbackUrl, Mode.UNSUBSCRIBE, topicUrl);
	}

	Mono<ResponseEntity<Void>> subscribe(URL topicUrl, URL callbackUrl, Verify verify, long leaseInSeconds,
			String verifyToken) {
		return this.changeSubscription(leaseInSeconds, verify, verifyToken, callbackUrl, Mode.SUBSCRIBE, topicUrl);
	}

	private Mono<ResponseEntity<Void>> changeSubscription(long leaseSeconds, Verify verify, String verifyToken,
			URL callbackUrl, Mode mode, URL topicUrl) {
		var required = Map.of(//
				"hub.verify", verify.name().toLowerCase(), //
				"hub.mode", mode.name().toLowerCase(), //
				"hub.callback", callbackUrl.toExternalForm(), //
				"hub.topic", topicUrl.toExternalForm() //
		);
		var map = new HashMap<>(required);
		if (StringUtils.hasText(verifyToken))
			map.put("hub.verify_token", verifyToken);
		if (leaseSeconds > 0)
			map.put("hub.lease_seconds", Long.toString(leaseSeconds));
		var mvm = new LinkedMultiValueMap<String, String>();
		for (var k : map.keySet())
			mvm.put(k, List.of(map.get(k)));
		return this.http.post()//
				.uri(this.hubUrl.toExternalForm()) //
				.body(BodyInserters.fromMultipartData(mvm)) //
				.retrieve() //
				.toBodilessEntity();
	}

}

@Slf4j
@Configuration
@RequiredArgsConstructor
class EventListenerConfiguration {

	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private final YoutubeService service;

	private final ApplicationEventPublisher publisher;

	@EventListener
	void videoCreated(YoutubeVideoCreatedEvent videoCreatedEvent) {
		log.info("YoutubeVideoCreatedEvent");
		log.info("need to promote: {} ", videoCreatedEvent.video().videoId() + ':' + videoCreatedEvent.video().title());
	}

	// hack: periodically force a re-evaluation of the data
	@EventListener(ApplicationReadyEvent.class)
	void kickoff() {
		log.info("ApplicationReadyEvent");
		this.executorService.submit(() -> this.publisher.publishEvent(new YoutubeChannelUpdatedEvent(Instant.now())));
		this.scheduledExecutorService.schedule(
				() -> this.publisher.publishEvent(new YoutubeChannelUpdatedEvent(Instant.now())), 1, TimeUnit.HOURS);
	}

	@EventListener(YoutubeChannelUpdatedEvent.class)
	void youtubeChannelUpdated() {
		log.info("YoutubeChannelUpdatedEvent");
		service.refresh();
	}

}

interface YoutubeService {

	void refresh();

	List<Video> videos();

}

@Slf4j
@Controller
@ResponseBody
@RequiredArgsConstructor
@CrossOrigin(origins = { "http://192.168.4.218:8081", "http://localhost:8081", "https://coffee-software-show.github.io",
		"http://www.coffeesoftware.com", "https://www.coffeesoftware.com", "http://coffeesoftware.com",
		"https://coffeesoftware.com" })
class YoutubeController {

	private final YoutubeService service;

	private final ApplicationEventPublisher publisher;

	@RequestMapping(value = "/refresh-2", method = { RequestMethod.GET, RequestMethod.POST })
	ResponseEntity<?> refresh2(RequestEntity<String> payload,
			@RequestParam(value = "hub.challenge", required = false) String hubChallenge) {

		log.info("/refresh-2!!!");
		if (StringUtils.hasText(hubChallenge)) {
			log.info("got the hub challenge " + hubChallenge);
			return ResponseEntity.ok(hubChallenge);
		}

		log.info("webhook update!");
		log.info("headers");
		payload.getHeaders().forEach((k, v) -> log.info('\t' + k + '=' + String.join(",", v)));
		log.info("payload");
		log.info("" + (payload.getBody()));
		this.publisher.publishEvent(new YoutubeChannelUpdatedEvent(Instant.now()));
		return ResponseEntity.status(204).build();
	}

	@RequestMapping(value = "/refresh", method = { RequestMethod.GET, RequestMethod.POST })
	ResponseEntity<?> refresh(RequestEntity<String> payload,
			@RequestParam(value = "hub.challenge", required = false) String hubChallenge) {

		if (StringUtils.hasText(hubChallenge)) {
			log.info("got the hub challenge " + hubChallenge);
			return ResponseEntity.ok(hubChallenge);
		}

		log.info("webhook update!");
		log.info("headers");
		payload.getHeaders().forEach((k, v) -> log.info('\t' + k + '=' + String.join(",", v)));
		log.info("payload");
		log.info("" + (payload.getBody()));
		this.publisher.publishEvent(new YoutubeChannelUpdatedEvent(Instant.now()));
		return ResponseEntity.status(204).build();
	}

	@GetMapping("/videos")
	Collection<Video> videos() {
		return this.service.videos();
	}

}

@Slf4j
@RequiredArgsConstructor
class DefaultYoutubeService implements YoutubeService {

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
				.thenMany(this.databaseClient.sql("select * from youtube_videos ").fetch().all().map(mapVideoFunction)) //
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
