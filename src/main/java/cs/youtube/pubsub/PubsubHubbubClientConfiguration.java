package cs.youtube.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
class PubsubHubbubClientConfiguration {

	private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

	@Bean
	DefaultPubsubHubbubClient pubsubHubbubClient(WebClient http) {
		return new DefaultPubsubHubbubClient(http, url("https://pubsubhubbub.appspot.com"));
	}

	@Bean
	ApplicationListener<ApplicationReadyEvent> readyEventApplicationListener(PubsubHubbubClient pubsubHubbubClient) {
		return event -> {
			var leaseInSeconds = 60 * 60 * 2;
			subscribe(pubsubHubbubClient, leaseInSeconds);
			scheduledExecutorService.schedule(() -> subscribe(pubsubHubbubClient, leaseInSeconds), 1, TimeUnit.HOURS);
		};
	}

	@NonNull
	private static URL url(String url) {
		try {
			return new URL(url);
		}
		catch (Throwable throwable) {
			log.error("ooops!", throwable);
		}
		throw new IllegalArgumentException("the URL could not be created!");
	}

	private static void subscribe(PubsubHubbubClient pubsubHubbubClient, int leaseInSeconds) {
		var topicUrl = url("https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCjcceQmjS4DKBW_J_1UANow");
		var callbackUrl = url("https://api.coffeesoftware.com/reset");
		pubsubHubbubClient.subscribe(topicUrl, callbackUrl, PubsubHubbubClient.Verify.SYNC, leaseInSeconds, null)
				.subscribe(re -> log.info("subscribed to " + topicUrl.toExternalForm()));
	}

}
