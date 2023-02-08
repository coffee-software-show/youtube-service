package cs.youtube;

import cs.youtube.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

@EnableConfigurationProperties(YoutubeProperties.class)
@SpringBootApplication
public class YoutubeApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoutubeApplication.class, args);
    }

    @Bean
    WebClient client(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    YoutubeService youtubeService(YoutubeClient client, YoutubeProperties properties,
                                  DatabaseClient databaseClient) {
        return new YoutubeService(client, properties.channelId(),
                databaseClient);
    }

}

@Slf4j
@RequiredArgsConstructor
class YoutubeService {

    private final YoutubeClient client;

    private final String channelId;

    private final DatabaseClient databaseClient;

    @EventListener(ApplicationReadyEvent.class)
    void begin() throws Exception {
        doReset();
    }

    @EventListener
    void refresh(YoutubeChannelUpdatedEvent channelUpdatedEvent) throws Exception {
        doReset();
    }

    private final String insertSql = """
                        
            insert into youtube_videos(
                video_id     ,
                title        ,
                description  ,
                published_at ,
                thumbnail_url,
                category_id  ,
                view_count   ,
                like_count   ,
                favorite_count,
                comment_count
            )
            values(  
                :video_id     ,
                :title        ,
                :description  ,
                :published_at ,
                :thumbnail_url,
                :category_id  ,
                :view_count   ,
                :like_count   ,
                :favorite_count,
                :comment_count 
            ) ;
            """;


    private void doReset() throws Exception {
        var fluxOfVideos = this.client.getChannelById(this.channelId)
                .flatMapMany(channel -> this.client.getAllVideosByChannel(channel.channelId()))
                .flatMap(video -> this.databaseClient
                        .sql(this.insertSql)
                        .bind( "video_id", video.videoId())
                        .bind( "title", video.title())
                        .bind( "description", video.description())
                        .bind( "published_at", video.publishedAt())
                        .bind( "thumbnail_url", video.standardThumbnail(). toExternalForm())
                        .bind( "category_id", video.categoryId())
                        .bind( "view_count", video.viewCount())
                        .bind( "like_count", video.likeCount())
                        .bind( "favorite_count", video.favoriteCount())
                        .bind( "comment_count", video.commentCount())
                        .fetch()
                        .rowsUpdated()
                );
        fluxOfVideos
                .doOnError(throwable -> log.error(throwable.getMessage()))
                .subscribe(System.out::println);


    }

}
