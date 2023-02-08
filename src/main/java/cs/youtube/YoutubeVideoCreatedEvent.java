package cs.youtube;

import cs.youtube.client.Video;

/**
 * Signalled when there's a new video to promote that hasn't yet been promoted.
 *
 */
public record YoutubeVideoCreatedEvent(Video video) {
}
