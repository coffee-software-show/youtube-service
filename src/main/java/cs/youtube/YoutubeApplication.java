package cs.youtube;

import cs.youtube.client.YoutubeClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

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
