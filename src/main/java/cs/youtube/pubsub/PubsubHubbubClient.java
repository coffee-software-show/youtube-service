package cs.youtube.pubsub;

import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.net.URL;

/**
 * provides support for working with the google cloud pubsub hub eventing mechanism so
 * that i can receive notifications from google's youtube data api.
 *
 * @author Josh Long
 */
interface PubsubHubbubClient {

	public enum Verify {

		ASYNC, SYNC

	}

	public enum Mode {

		SUBSCRIBE, UNSUBSCRIBE

	}

	Mono<ResponseEntity<Void>> unsubscribe(URL topicUrl, URL callbackUrl, Verify verify, long leaseInSeconds,
			String verifyToken);

	Mono<ResponseEntity<Void>> subscribe(URL topicUrl, URL callbackUrl, Verify verify, long leaseInSeconds,
			String verifyToken);

}
