package cs.youtube;

import cs.youtube.client.Video;

import java.util.List;

interface YoububeService {

	void refresh();

	List<Video> videos();

}
