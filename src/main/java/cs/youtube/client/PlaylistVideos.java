package cs.youtube.client;

import java.util.Collection;

/**
 * An aggregate over a single page of {@link Playlist} results, comprised of {@link Video}
 * entries.
 * @param playlistId
 * @param videos
 * @param nextPageToken
 * @param previousPageToken
 * @param resultsPerPage
 * @param totalResults
 */
public record PlaylistVideos(String playlistId, Collection<Video> videos, String nextPageToken,
		String previousPageToken, int resultsPerPage, int totalResults) {
}
