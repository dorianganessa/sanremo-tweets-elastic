package fyi.bigdata.sanremotweet

import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.Seconds
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.twitter.TwitterUtils
import org.elasticsearch.spark.rdd.EsSpark

import com.google.gson.Gson

import fyi.bigdata.sanremotweet.utility.Utils
import twitter4j.Status


object TweetCollector {
  
  def filterTweets(tweet: Status):Boolean = {
    val text= tweet.getText.toLowerCase()
    return text.contains("sanremo")
  }
  
  private var numTweetsCollected = 0L
  private var gson = new Gson
  
  val numTweetsToCollect=9999999
  val intervalSecs=600
  val partitionsEachInterval=1
  
  def main(args: Array[String]) {

    Utils.setupTwitter()
    Utils.setupLogging()
    
  
    
    println("Initializing Streaming Spark Context...")
    
    val spark = SparkSession
      .builder
      .appName(this.getClass.getSimpleName)
      .master("local[*]")
      .getOrCreate()
      
    val ssc = new StreamingContext(spark.sparkContext, Seconds(intervalSecs))

    val tweetStream = TwitterUtils.createStream(ssc, None)
        .filter { filterTweets }
        .map {gson.toJson(_)}
//        .map { t => Tweet(t.getUser.getName,t.getText,t.getCreatedAt)} 
        .foreachRDD((rdd, time) => {
            val count = rdd.count()
            if (count > 0) {
              EsSpark.saveJsonToEs(rdd, "twitter-sanremo/tweet")
              numTweetsCollected += count
              if (numTweetsCollected > numTweetsToCollect) {
                  System.exit(0)
              }
            }
    })

    ssc.start()
    ssc.awaitTermination()
  }
}