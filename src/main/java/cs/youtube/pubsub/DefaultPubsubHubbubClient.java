package cs.youtube.pubsub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
class DefaultPubsubHubbubClient implements PubsubHubbubClient {

	private final WebClient http;

	private final URL hubUrl;

	@Override
	public Mono<ResponseEntity<Void>> unsubscribe(URL topicUrl, URL callbackUrl, PubsubHubbubClient.Verify verify,
			long leaseInSeconds, String verifyToken) {
		return this.changeSubscription(leaseInSeconds, verify, verifyToken, callbackUrl,
				PubsubHubbubClient.Mode.UNSUBSCRIBE, topicUrl);
	}

	@Override
	public Mono<ResponseEntity<Void>> subscribe(URL topicUrl, URL callbackUrl, PubsubHubbubClient.Verify verify,
			long leaseInSeconds, String verifyToken) {
		return this.changeSubscription(leaseInSeconds, verify, verifyToken, callbackUrl,
				PubsubHubbubClient.Mode.SUBSCRIBE, topicUrl);
	}

	private Mono<ResponseEntity<Void>> changeSubscription(long leaseSeconds, PubsubHubbubClient.Verify verify,
			String verifyToken, URL callbackUrl, PubsubHubbubClient.Mode mode, URL topicUrl) {
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
		return this.http//
				.post()//
				.uri(this.hubUrl.toExternalForm()) //
				.body(BodyInserters.fromMultipartData(mvm)) //
				.retrieve() //
				.onStatus( //
						httpStatusCode -> httpStatusCode.isError() && httpStatusCode.value() == 409, //
						clientResponse -> { //
							log.warn(
									"got a conflict, which means the subscription already exists. so, skipping for now.");
							return Mono.empty();
						} //
				) //
				.toBodilessEntity();
	}

}
