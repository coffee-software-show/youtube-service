package cs.youtube.utils;

import lombok.SneakyThrows;

import java.net.URL;

public abstract class UrlUtils {

	@SneakyThrows
	public static URL url(String url) {
		return new URL(url);
	}

}
