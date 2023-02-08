package cs.youtube.client;

import java.util.Date;

public record Channel(String channelId, String title, String description, Date publishedAt) {
}
