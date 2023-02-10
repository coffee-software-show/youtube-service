package cs.youtube;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Instant;

@Slf4j
@Configuration
@RequiredArgsConstructor
class EventListenerConfiguration {

	private final YoububeAggregate service;

	private final ApplicationEventPublisher publisher;

	@EventListener(ApplicationReadyEvent.class)
	void kickoff() {
		this.publisher.publishEvent(new YoutubeChannelUpdatedEvent(Instant.now()));
	}

	@EventListener
	void videoCreated(YoutubeVideoCreatedEvent videoCreatedEvent) {
		log.info("need to promote: {}", videoCreatedEvent.video().videoId() + ':' + videoCreatedEvent.video().title());
	}

	@EventListener(YoutubeChannelUpdatedEvent.class)
	void youtubeChannelUpdated() {
		service.refresh();
	}

}
