package cs.youtube.actuate;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@ManagementContextConfiguration
class YoutubeActuatorEndpointConfiguration {

	@Bean
	YoutubeActuatorEndpoint youtubeActuatorEndpoint(ApplicationEventPublisher publisher) {
		return new YoutubeActuatorEndpoint(publisher);
	}

}
