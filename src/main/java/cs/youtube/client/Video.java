package cs.youtube.client;

import java.net.URL;
import java.util.Date;
import java.util.List;

public record Video(String videoId, String title,
					String description, Date publishedAt,
					URL standardThumbnail,
		List<String> tags, int categoryId, int viewCount,
					int likeCount, int favoriteCount,
					int commentCount,
		String channelId) {
}
