package cs.youtube.client;

import java.util.Collection;

/**
 * holder for the results coming back from the pagination methods.
 * @param channelId
 * @param videos
 */
public record ChannelVideos(String channelId, Collection<Video> videos, String nextPageToken,
		String previousPageToken) {
}
