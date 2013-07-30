package controllers

import play.api._
import play.api.mvc._
import twitter4j.TwitterFactory
import twitter4j.Query
import twitter4j.Twitter
import twitter4j.conf.ConfigurationBuilder
import scala.collection.JavaConversions._
import scala.util.matching.Regex
import scala.collection.mutable.HashMap
import dispatch._
import scala.concurrent._
import java.util.Date
import ExecutionContext.Implicits.global
import com.github.theon.uri.Uri._


object Application extends Controller {

  case class Tweet(tweetId: Long, userId: Long, url: String, created: Date, retweetCount: Long)
  case class LinkData(url: String, var tweetIds: HashMap[Long, Unit], ageInSeconds: Long, totalKlout: Long)
  private var TweetList = new HashMap[String, LinkData]

  private def processTweets = {
      // (1) config work to create a twitter object
      val cb = new ConfigurationBuilder()
    cb.setDebugEnabled(true)
      .setOAuthConsumerKey("gGq2qBSmloo9sYMb68uvQ")
      .setOAuthConsumerSecret("kvhzqrWMD957hbrHFILFkBiqeuAmQ8WYQ2eneXu1Y")
      .setOAuthAccessToken("25308146-7WdXlUKRVncPzyBecWRHjXTLJAnT4gfV50Pr75NTe")
      .setOAuthAccessTokenSecret("JjBMMZfC39xavaKXILzGA5D2HEmr8KjEUQfEf8nTMO0")

    val tf = new TwitterFactory(cb.build())
    val twitter = tf.getInstance()

    val query = new Query("#nyc").count(100).resultType("RECENT")

    val result = twitter.search(query)
    val urlRegEx = """http://[A-Za-z0-9-_]+\.[A-Za-z0-9-_:%&\?/.=]+""".r

    result.getTweets.foreach(tweet => {
      for (url <- urlRegEx findAllIn tweet.getText; if url.length > 0)
      {
        val fixedUrl = if (url.takeRight(1) == ".") url.dropRight(1) else url

        for (str <- Http(getLongUrl(fixedUrl) OK as.xml.Elem))  {
          val elem = str \\ "long-url"
          var uri = parseUri(elem.text).toString
          val tw = new Tweet(tweet.getId, tweet.getUser.getId, uri, tweet.getCreatedAt, tweet.getRetweetCount)
          inspectTweet(tw)
        }
      }
    })

    val sorted = TweetList.values.toList.sortWith(_.tweetIds.size > _.tweetIds.size)
    //println(TweetList)
    sorted.foreach(x => println(x.url + " - " + x.tweetIds.size))

  }

  private def inspectTweet(tweet: Tweet): Unit = {
     var tweetData = TweetList.get(tweet.url) match {
       case data: Some[LinkData] => data.get
       case None => new LinkData(url = tweet.url, new HashMap[Long, Unit], 0, 0)
     }

     if (tweetData.tweetIds contains tweet.tweetId) return ()
     tweetData.tweetIds += tweet.tweetId -> ()
     TweetList.put(tweet.url, tweetData)
  }


  def getLongUrl(url: String) = {
    host("api.longurl.org") / "v2" / "expand" <<? Map("url" -> url)
  }


  def index = Action {

    processTweets

    Ok(views.html.index("Your new application is ready."))
  }

}