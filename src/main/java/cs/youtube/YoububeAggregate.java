package cs.youtube;

import cs.youtube.client.Video;

import java.util.List;

interface YoububeAggregate {

	void refresh();

	List<Video> videos();

}
