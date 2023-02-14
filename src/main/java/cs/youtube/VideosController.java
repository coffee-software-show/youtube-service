package cs.youtube;

import cs.youtube.client.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Controller
@ResponseBody
@RequiredArgsConstructor
@CrossOrigin(origins = { "http://192.168.4.218:8081", "http://localhost:8081", "https://coffee-software-show.github.io",
		"http://www.coffeesoftware.com", "https://www.coffeesoftware.com", "http://coffeesoftware.com",
		"https://coffeesoftware.com" })
class VideosController {

	private final Executor executor = Executors.newSingleThreadExecutor();

	private final YoububeService service;

	private final ApplicationEventPublisher publisher;

	@RequestMapping(value = "/reset", method = { RequestMethod.GET, RequestMethod.POST })
	ResponseEntity<?> reset(RequestEntity<String> payload,
			@RequestParam(value = "hub.challenge", required = false) String hubChallenge) {
		log.info("========================================");
		log.info("resetting...");
		if (StringUtils.hasText(hubChallenge)) {
			log.info("hub challenge " + hubChallenge);
			return ResponseEntity.ok(hubChallenge);
		}
		payload.getHeaders().forEach((k, v) -> log.info('\t' + k + '=' + String.join(",", v)));
		log.info("payload: " + payload.getBody());
		this.executor.execute(() -> publisher.publishEvent(new YoutubeChannelUpdatedEvent(Instant.now())));
		return ResponseEntity.status(204).build();
	}

	@GetMapping("/videos")
	Collection<Video> videos() {
		return this.service.videos();
	}

}
