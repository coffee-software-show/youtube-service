package cs.youtube;

import com.joshlong.google.pubsubhubbub.PubsubHubbubClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.net.URL;
import java.time.Instant;
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

	@EventListener
	void videoCreated(YoutubeVideoCreatedEvent videoCreatedEvent) {
		log.info("need to promote: {}", videoCreatedEvent.video().videoId() + ':' + videoCreatedEvent.video().title());
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

	@SneakyThrows
	private static URL url(String url) {
		return new URL(url);
	}

	private static void subscribe(PubsubHubbubClient pubsubHubbubClient, int leaseInSeconds) {
		var topicUrl = url("https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCjcceQmjS4DKBW_J_1UANow");
		var callbackUrl = url("https://api.coffeesoftware.com/reset");
		pubsubHubbubClient //
				.subscribe(topicUrl, callbackUrl, PubsubHubbubClient.Verify.SYNC, leaseInSeconds, null)
				.subscribe(re -> log.info("subscribed to " + topicUrl.toExternalForm()));
	}

}
