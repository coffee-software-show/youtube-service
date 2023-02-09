# Youtube Promotion 

## Things to look into 
* use the youtube data api to receive [pubsub webhook notifications when a new video has been published ](https://pubsubhubbub.appspot.com/subscribe) and the [Youtube push notification API](https://developers.google.com/youtube/v3/guides/push_notifications). This requires a few key elements, which we've since built into the service:
  * [a callback URI](https://api.coffeesoftware.com/refresh)
  * a topic URL, which is [the RSS/ATOM URL for the channel itself]()
