package benchmark

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class EloqKVSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // Feeder for random keys
  val keyFeeder = Iterator.continually(Map(
    "key"   -> s"gatling:${java.util.UUID.randomUUID()}",
    "field" -> s"f${(math.random() * 100).toInt}",
    "score" -> (math.random() * 1000).toInt.toString,
    "value" -> s"val-${System.currentTimeMillis()}"
  ))

  val setScenario = scenario("Set Operations")
    .feed(keyFeeder)
    .exec(
      http("SET")
        .post("/api/set")
        .queryParam("key", "#{key}")
        .queryParam("value", "#{value}")
        .check(status.is(200))
    )
    .pause(1.milliseconds, 5.milliseconds)
    .exec(
      http("GET")
        .get("/api/get")
        .queryParam("key", "#{key}")
        .check(status.is(200))
    )

  val hashScenario = scenario("Hash Operations")
    .feed(keyFeeder)
    .exec(
      http("HSET")
        .post("/api/hset")
        .queryParam("key", "gatling:hash:#{key}")
        .queryParam("field", "#{field}")
        .queryParam("value", "#{value}")
        .check(status.is(200))
    )
    .pause(1.milliseconds, 5.milliseconds)
    .exec(
      http("HGET")
        .get("/api/hget")
        .queryParam("key", "gatling:hash:#{key}")
        .queryParam("field", "#{field}")
        .check(status.is(200))
    )

  val zsetScenario = scenario("Sorted Set Operations")
    .feed(keyFeeder)
    .exec(
      http("ZADD")
        .post("/api/zadd")
        .queryParam("key", "gatling:zset:leaderboard")
        .queryParam("score", "#{score}")
        .queryParam("member", "#{key}")
        .check(status.is(200))
    )

  val randomWriteScenario = scenario("Random Write")
    .exec(
      http("Random Write")
        .get("/api/random-write")
        .check(status.is(200))
    )

  val pingScenario = scenario("Ping")
    .exec(
      http("PING")
        .get("/api/ping")
        .check(status.is(200))
    )

  setUp(
    setScenario.inject(
      rampUsers(50).during(10.seconds),
      constantUsersPerSec(30).during(30.seconds)
    ),
    hashScenario.inject(
      rampUsers(20).during(10.seconds),
      constantUsersPerSec(15).during(30.seconds)
    ),
    zsetScenario.inject(
      rampUsers(20).during(10.seconds),
      constantUsersPerSec(10).during(30.seconds)
    ),
    randomWriteScenario.inject(
      rampUsers(30).during(10.seconds),
      constantUsersPerSec(20).during(30.seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.max.lt(2000),
     global.successfulRequests.percent.gt(99.0)
   )
}
