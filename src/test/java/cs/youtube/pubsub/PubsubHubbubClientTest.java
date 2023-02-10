package cs.youtube.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.net.URL;

class PubsubHubbubClientTest {

	private final static Logger log = LoggerFactory.getLogger(PubsubHubbubClientTest.class.getName());

	private final WebClient http = WebClient.create();

	private final PubsubHubbubClient client = new DefaultPubsubHubbubClient(this.http,
			url("https://pubsubhubbub.appspot.com"));

	private static URL url(String url) {
		try {
			return new URL(url);
		}
		catch (Throwable throwable) {
			log.error("ooops!", throwable);
		}
		return null;
	}

	// @Test
	void subscribe() throws Exception {
		var topicUrl = url("https://www.youtube.com/xml/feeds/videos.xml?channel_id=UCjcceQmjS4DKBW_J_1UANow");
		var callbackUrl = url("https://api.coffeesoftware.com/refresh-2");
		var leaseInSeconds = 120;
		var result = this.client
				.unsubscribe(topicUrl, callbackUrl, PubsubHubbubClient.Verify.ASYNC, leaseInSeconds, null)
				.thenMany(this.client.subscribe(topicUrl, callbackUrl, PubsubHubbubClient.Verify.ASYNC, leaseInSeconds,
						null));
		StepVerifier //
				.create(result) //
				.consumeNextWith(re -> {
					log.info(re.getStatusCode().value() + "");
					re.getHeaders().forEach((key, value) -> log.info(key + '=' + value));
				}) //
				.verifyComplete();
	}

}