package cs.youtube.actuate;

import cs.youtube.YoutubeChannelUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Map;

/**
 * todo hook this up to a webhook from Youtube Data API
 *
 */

@Endpoint(id = "youtube")
@RequiredArgsConstructor
class YoutubeActuatorEndpoint {

	private final ApplicationEventPublisher publisher;

	@ReadOperation
	Map<String, Object> reset() {
		Instant now = Instant.now();
		this.publisher.publishEvent(new YoutubeChannelUpdatedEvent(now));
		return Map.of("when", now);
	}

}
