package cs.youtube;

import com.joshlong.google.pubsubhubbub.PubsubHubbubClient;
import com.joshlong.twitter.Twitter;
import cs.youtube.utils.UrlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
class EventListenerConfiguration {

	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

	private final YoububeAggregate service;

	private final ApplicationEventPublisher publisher;

	private final PubsubHubbubClient pubsubHubbubClient;

	private final Twitter twitter;

	private final DatabaseClient databaseClient;

	@EventListener
	void videoCreated(YoutubeVideoCreatedEvent videoCreatedEvent) {
		log.info("need to promote: {}", videoCreatedEvent.video().videoId() + ':' + videoCreatedEvent.video().title());
		var scheduled = new Date();
		twitter.scheduleTweet(scheduled, "starbuxman",
				videoCreatedEvent.video().title() + " " + "https://www.youtube.com/watch?v="
						+ videoCreatedEvent.video().videoId(),
				null) //
				.flatMap(promoted -> databaseClient//
						.sql(" update promoted_youtube_videos set promoted_at = :when where video_id = :videoId ")//
						.bind("when", scheduled) //
						.bind("videoId", videoCreatedEvent.video().videoId())//
						.fetch() //
						.rowsUpdated()//
				) //
				.filter(count -> count > 0) //
				.subscribe(rows -> log.info("successfully promoted " + videoCreatedEvent.video().videoId()
						+ " with title " + videoCreatedEvent.video().title()));
	}

	@EventListener(YoutubeChannelUpdatedEvent.class)
	void youtubeChannelUpdated() {
		service.refresh();
	}

	@EventListener(ApplicationReadyEvent.class)
	void ready() {
		var leaseInSeconds = 60 * 60 * 2;
		subscribe(pubsubHubbubClient, leaseInSeconds);
		scheduledExecutorService.schedule(() -> subscribe(pubsubHubbubClient, leaseInSeconds), 1, TimeUnit.HOURS);
		this.publisher.publishEvent(new YoutubeChannelUpdatedEvent(Instant.now()));
	}

	private static void subscribe(PubsubHubbubClient pubsubHubbubClient, int leaseInSeconds) {
		var topicUrl = UrlUtils.url("https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCjcceQmjS4DKBW_J_1UANow");
		var callbackUrl = UrlUtils.url("https://api.coffeesoftware.com/reset");
		pubsubHubbubClient //
				.subscribe(topicUrl, callbackUrl, PubsubHubbubClient.Verify.SYNC, leaseInSeconds, null)
				.subscribe(re -> log.info("subscribed to " + topicUrl.toExternalForm()));
	}

}
