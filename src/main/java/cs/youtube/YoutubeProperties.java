package cs.youtube;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("youtube")
public record YoutubeProperties(String channelId, String dataApiKey) {
}
