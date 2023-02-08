package cs.youtube.client;

import java.util.List;

/**
 * An aggregate for all the returned {@link Playlist} videos
 * @param channelId
 * @param playlists
 */
public record ChannelPlaylists(String channelId, List<Playlist> playlists, int totalResults, String previousPageToken,
		String nextPageToken) {
}
