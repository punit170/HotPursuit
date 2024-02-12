package controllers

import org.apache.pekko.stream.Materializer
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.libs.json.Json
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

//Tests for the service
class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "GET /" should {

    "receive \"gameOn\":\"true\" when requested /startGame from a new instance of controller" in {

      implicit val ec1: ExecutionContext = ExecutionContext.Implicits.global
      val controller1 = inject[HomeController]

      val startGame = controller1.startGame().apply(FakeRequest(GET, "/startGame"))

      status(startGame) mustBe OK
      contentType(startGame) mustBe Some("application/json")
      contentAsString(startGame) must include("\"gameOn\":\"true\"")
    }

    "receive plain text with instructions that contains \"Welcome to HostPursuit!\" when requested /game/instructions from a new instance of controller" in {

      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
      val controller = inject[HomeController]

      val game_instructions = controller.showGameInstructions().apply(FakeRequest(GET, "/game/instructions"))

      status(game_instructions) mustBe OK
      contentType(game_instructions) mustBe Some("text/plain")
      contentAsString(game_instructions) must include("Welcome to HostPursuit!")
    }

    "receive \"gameOn\":\"false\" when requested /stopGame after /startGame from a new instance of controller" in {

      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
      val controller = inject[HomeController]

      val startGame = controller.startGame().apply(FakeRequest(GET, "/startGame"))
      val stopGame: Future[Result] = startGame.flatMap { _ =>
        controller.stopGame().apply(FakeRequest(GET, s"/stopGame"))
      }

      status(stopGame) mustBe OK
      contentType(stopGame) mustBe Some("application/json")
      contentAsString(stopGame) must include("\"gameOn\":\"false\"")
    }
  }

  "PUT /" should {

    "update selected players and send back a response with \"player1\"=\"policeman\" and \"player2\"=\"thief\" in lowercases, after the game starts" in {

      implicit val ec2: ExecutionContext = ExecutionContext.Implicits.global
      val controller2 = inject[HomeController]
      implicit val materializer: Materializer = app.materializer
      val startGameRes = controller2.startGame().apply(FakeRequest(GET, "/startGame"))

      val selectPlayers: Future[Result] = startGameRes.flatMap { _ =>
        val jsonBody = Json.obj("player1" -> "POLICEMAN", "player2" -> "THIEF")
        println(jsonBody)
        val fakeRequest = FakeRequest(PUT, s"/game/selectplayers").withHeaders("Content-Type" -> "application/json")
        controller2.selectPlayers().apply(fakeRequest.withBody(jsonBody))
      }

      status(startGameRes) mustBe OK
      status(selectPlayers) mustBe OK
      contentType(selectPlayers) mustBe Some("application/json")
      println(contentAsString(selectPlayers))
      contentAsString(selectPlayers) must include("\"player1\":\"policeman\",\"player2\":\"thief\"")
    }
  }

    "receive json data when requested /game/getStatusData, if the game is on, and players have been selected" in {

      implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
      val controller = inject[HomeController]
      implicit val materializer: Materializer = app.materializer
      val startGameRes = controller.startGame().apply(FakeRequest(GET, "/startGame"))

      val getStatusRes: Future[Result] = startGameRes.flatMap { _ =>
        val jsonBody = Json.obj("player1" -> "Policeman", "player2" -> "Thief")
        println(jsonBody)
        val fakeRequest = FakeRequest(PUT, s"/game/selectplayers").withHeaders("Content-Type" -> "application/json")
        val selectPlayerRes = controller.selectPlayers().apply(fakeRequest.withBody(jsonBody))
        selectPlayerRes.flatMap{_ =>
          val fakeRequest = FakeRequest(GET, s"/game/getStatusData")
          controller.getStatusData().apply(fakeRequest)
        }
      }
      status(getStatusRes) mustBe OK
      contentType(getStatusRes) mustBe Some("application/json")

    }

}

