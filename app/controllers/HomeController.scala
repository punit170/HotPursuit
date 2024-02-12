package controllers

import scala.util.Random

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import models.LogicHelperFunctions
import models.LogicHelperFunctions.logger
//to use the ws-library of the play framework
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws._


/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(ws: WSClient, implicit val ec: ExecutionContext, val controllerComponents: ControllerComponents) extends BaseController {

  /**
   * The configuration in the `routes` file means that these methods
   * will be called when the application receives an http-method request with
   * a path of `/`.
   * The below controller methods implicitly call respective functions from LogicHelperFunctions for running the game
   */

  //controller method to start the game. Outputs 'gameOn' variable's state in json-parsable format
  def startGame(): Action[AnyContent] = Action {
    LogicHelperFunctions.setGame(true)
    val gameOnData = Json.obj(
      "gameOn" -> LogicHelperFunctions.isGameOn()
    )
    logger.info(s"startGame response-> ${gameOnData.toString()}")
    Ok(gameOnData)
  }

  //controller method to stop the game. Outputs 'gameOn' variable's state in json-parsable format
  def stopGame(): Action[AnyContent] = Action {
    LogicHelperFunctions.resetEnvTables()
    val gameOffData = Json.obj(
      "gameOn" -> LogicHelperFunctions.isGameOn()
    )
    logger.info(s"stopGame response-> ${gameOffData.toString()}")
    Ok(gameOffData)
  }

  //controller method to reset the game and output 'gameOn' and 'winner'
  def postGameOver(): JsValue = {
    val winner = LogicHelperFunctions.getWinner()
    LogicHelperFunctions.resetEnvTables()
    val gameOverData: JsValue = Json.obj(
      "gameOn" -> LogicHelperFunctions.isGameOn(),
      "winner" -> winner
    )
    logger.info(s"Game Over! response-> ${gameOverData.toString()}")
    gameOverData
  }

  //controller method to output instructions informing how to play the game
  def showGameInstructions(): Action[AnyContent] = Action {
    val gameInstructions = "Welcome to HostPursuit!" +
      "\n\n How to Play: " +
      "\n\t1.To start the game:  make a GET @ http://localhost:9000/startGame " +
      "\n\t2.To select players:  make a PUT request @ http://localhost:9000/game/selectplayers with json data as follows \n\t\t- {\"player1\":\"policeman\"(or \"thief\"), \"player2\":\"thief\"(or \"policeman\")}" +
      "\n\t3. To see status: make a \'GET\' request @ http://localhost:9000/game/getStatus\n\t4. To get json-parsable status data- make a \'GET\' request @ http://localhost:9000/game/getStatusData" +
      "\n\t5. To stop the game: make a \'GET\' request @ http://localhost:9000/stopGame" +
      "\n\t6. To see instructions: make a \'GET\' request @ http://localhost:9000/game/instructions" +
      "\n\n The Game is " + {if (LogicHelperFunctions.isGameOn() == "true") "\"ON\"" else "\"OFF\""} + "! Use the REST requests to play!"
    logger.info(s"Game Instructions! response-> $gameInstructions")
    Ok(gameInstructions)
  }

  //case class to help parse players' input in json-format
  case class Players(player1: String, player2: String)
  implicit val playersJson: OFormat[Players] = Json.format[Players]

  //controller method invoke logic function to select players when a PUT request arrives with json data as {"player1":"policeman"(or "thief"), "player2":"thief"(or "policeman")}
  def selectPlayers(): Action[JsValue] = Action(parse.json) { implicit request =>
    request.body.validate[Players].asOpt
      .fold {
        BadRequest("No item added")
      } {
        response =>
          if (LogicHelperFunctions.environmentTable("gameOn") == "false"){
            logger.error("selectplayers error! response-> The game has not started, and so players cannot be set. To start the game make a GET / request to http://localhost:9000/startGame")
            BadRequest("The game has not started, and so players cannot be set. To start the game make a GET / request to http://localhost:9000/startGame")
          }
          else {
            if (LogicHelperFunctions.environmentTable("player1") == "null" || LogicHelperFunctions.environmentTable("player2") == "null") {
              val validPlayersStrings = List("policeman", "thief")
              assert(validPlayersStrings.contains(response.player1.toLowerCase()))
              assert(validPlayersStrings.contains(response.player2.toLowerCase()))
              assert(response.player1 != response.player2)
              LogicHelperFunctions.setPlayers(response.player1.toLowerCase(), response.player2.toLowerCase())
              LogicHelperFunctions.setCurrentTurn("player1")
              LogicHelperFunctions.initPlayerPositions()
              /*val serve: String = "Players are now set!\n" +
                s"player1: ${LogicHelperFunctions.environmentTable("player1")}\n" +
                s"player2: ${LogicHelperFunctions.environmentTable("player2")}\n" +
                s"current turn: ${LogicHelperFunctions.environmentTable("currentTurn")}\n" +
                LogicHelperFunctions.gameTable.toString*/

              val selectPlayersData = Json.obj(
                "player1" -> LogicHelperFunctions.environmentTable("player1"),
                "player2" -> LogicHelperFunctions.environmentTable("player2")
              )
              logger.info(s"Players selected! response-> ${selectPlayersData.toString()}")
              Ok(selectPlayersData)
            }
            else {
              /*val serve: String = "Players already set! Cannot reset them, while the game is on!\n" +
                s"player1: ${LogicHelperFunctions.environmentTable("player1")}\n" +
                s"player2: ${LogicHelperFunctions.environmentTable("player2")}\n" +
                s"current turn: ${LogicHelperFunctions.environmentTable("currentTurn")}\n" +
                "\n\n You can stop the game by making a GET / request to http://localhost:9000/stopGame, and by making a GET / request to http://localhost:9000/startGame"
              Ok(serve)
              */
              logger.error("selectplayers error! response-> Players already set! Cannot reset them, while the game is on!")
              BadRequest("Players already set! Cannot reset them, while the game is on!")
            }
          }
      }
  }

  //controller method to invoke functions in order to move a player
  def movePlayer(playerNo: Int, nodeId: Int): Action[AnyContent] = Action {
    if(playerNo < 1 || playerNo > 2) {
      logger.error("move player error! response-> PlayerNo should be either 1 or 2. You can replay the move by making another PUT / request to http://localhost:9000/game/:playerNo/:nodeId with valid parameters!")
      BadRequest("PlayerNo should be either 1 or 2. You can replay the move by making another PUT / request to http://localhost:9000/game/:playerNo/:nodeId with valid parameters!")
    }
    else if (LogicHelperFunctions.getCurrentTurnPlayer() != ("player"+playerNo)) {
      logger.error(s"move player error! response-> Player$playerNo cannot make two moves consecutively!")
      BadRequest(s"Player$playerNo cannot make two moves consecutively!")
    }
    else if(LogicHelperFunctions.movePlayer(playerNo, nodeId)) {
      if(!LogicHelperFunctions.isGameOver())
        {
          LogicHelperFunctions.toggleTurn()
          /*val serve: String = s"Player$playerNo moved successfully!\n" +
            s"player1: ${LogicHelperFunctions.environmentTable("player1")}\t" +
            s"player2: ${LogicHelperFunctions.environmentTable("player2")}\n" +
            s"current turn: ${LogicHelperFunctions.environmentTable("currentTurn")}\n" +
            "\n\n (You can stop the game by making a GET / request to http://localhost:9000/stopGame, and by making a GET / request to http://localhost:9000/startGame)"
          */
          val statusData = LogicHelperFunctions.getStatusData()
          Ok(statusData)
        }
      else
        {
          /*val serve: String = s"Game Over!\n" +
            s"player1: ${LogicHelperFunctions.environmentTable("player1")}\t" +
            s"player2: ${LogicHelperFunctions.environmentTable("player2")}\n" +
            s"status: ${LogicHelperFunctions.gameTable.toString}" +
            s"\n${LogicHelperFunctions.getWinner()} WINS!"*/

          val statusData = LogicHelperFunctions.getStatusData()
          postGameOver()
          Ok(statusData)
        }
    }
    else {
      logger.error("move player error! response-> Invalid move!. Check nodeId / playerNo!")
      BadRequest("Invalid move!. Check nodeId / playerNo!")
    }
  }

  //controller function to obtain information about a player's own and the opponent's location nodes and the adjacent nodes.
  def getStatusData: Action[AnyContent] = Action {
    val data = LogicHelperFunctions.getStatusData()
    logger.info(s"getStatusData response-> ${data.toString()}")
    Ok(data)
  }

  /*
  // controller function to play the game automatically
  * makes implicit http-requests based on responses of previous implicit http-requests made, to play the game automatically
  * 1. a request is made to start the game
  * 2. a request is made to select players. 'player1' -> 'policeman', 'player2' -> 'thief'
  * 3. a request is made to get position data of players
  * 4. recursive turn-wise move-requests are made for players
  * 5. recursion stops when a player wins
  * */
  def auto(): Action[AnyContent] = Action.async {
    val startGameRequest: WSRequest = ws.url("http://localhost:9000/startGame")
    val startGameResponse: Future[WSResponse] = startGameRequest.withFollowRedirects(true).get()

    val res: Future[String] = startGameResponse.flatMap { firstresponse =>
      val firstresponseBody = firstresponse.body
//      println(s"Response from first GET request: $firstresponseBody")
      logger.info(s"Auto game- Response from the startGame request-> $firstresponseBody")
      val selectPlayersData = Json.obj("player1" -> "policeman", "player2" -> "thief")
      val selectPlayersURL = "http://localhost:9000/game/selectplayers"
      ws.url(selectPlayersURL).put(selectPlayersData).flatMap { secondResponse =>
        val secondResponseBody = secondResponse.body
        logger.info(s"Auto game- Response from the selectplayers request: $firstresponseBody")
//        println(s"Response from second PUT request: $secondResponseBody")

        val getStatusURL = "http://localhost:9000/game/getStatusData"
        ws.url(getStatusURL).get().flatMap { thirdResponse =>
          val thirdResponseBody = thirdResponse.body
//          println(s"Response from second PUT request: $thirdResponseBody")
          logger.info(s"Auto game- Response from getStatusData request: $firstresponseBody")

          if (LogicHelperFunctions.isGameOver())
          {
            val winner = LogicHelperFunctions.getWinner()
            postGameOver()
            logger.info(s"Auto game- Final winner-> $winner")
            Future(s"Combined result: \n$firstresponseBody \n\n$secondResponseBody\n\n$thirdResponseBody \n\n WINNER: $winner")
          }

          else {
            val acc: String = s"Combined result: \n$firstresponseBody \n\n$secondResponseBody\n\n$thirdResponseBody"
            makeMove(thirdResponse.body, acc)
          }

        }
      }
    }
    res.map(str => Ok(str))

  }

  //base condition for recursive move
  private def shouldStop(currStatus: JsValue): Boolean = {
    (currStatus \ "winner").as[String] != "null"
  }

  //recursive move function used in automatic game play
  private def makeMove(init: String, acc: String): Future[String] = {

    val statusJsonData: JsValue = Json.parse(init)

    val playerNo = (statusJsonData \ "currentTurn").as[Int]
    val nodeIds: List[List[Double]] = (statusJsonData \ s"player$playerNo" \ "outNodeIds").as[List[List[Double]]]
    val nodeId: Int = nodeIds(Random.nextInt(nodeIds.length)).head.toInt

    val movePlayeUrl = s"http://localhost:9000/game/move/$playerNo/$nodeId"
    ws.url(movePlayeUrl).put("").flatMap { response =>
      println(s"\n\nMOVE RESPONSE: ${response.body}")
      logger.info(s"\n\nAuto game- Response from move player request: ${response.body}")
      val jsonData = Json.parse(response.body)
      if (shouldStop(jsonData)) {
        val winner = (jsonData \ "winner").as[String]

        Future(acc+ "\n" + response.body + s"\n\n WINNER: $winner")

      }
      else {
        makeMove(response.body, acc + "\n" + response.body)
      }
    }
  }


  // functions for testing purpose.
  /* def getStatus(): Action[AnyContent] = Action {
     if (LogicHelperFunctions.isGameOn() == "true") {
       val serve: String = s"Game is ON!\n" +
         s"current turn: ${LogicHelperFunctions.environmentTable("currentTurn")}\n" +
         s"${LogicHelperFunctions.getStatus()}"
       Ok(serve)
     }
     else{
       Ok("Game is OFF! You can start the game by making a GET / request to http://localhost:9000/startGame")
     }
   }*/

  /*def autoMovePlayer(playerNo: Int, nodeId: Int): Action[AnyContent] = Action {
    if (playerNo < 1 || playerNo > 2)
      Ok("PlayerNo should be either 1 or 2. You can replay the move by making another PUT / request to http://localhost:9000/game/:playerNo/:nodeId with valid parameters!")
    else if (LogicHelperFunctions.getCurrentTurnPlayer() != ("player" + playerNo))
      Ok(s"Player$playerNo cannot make two moves consecutively!")

    else if (LogicHelperFunctions.movePlayer(playerNo, nodeId) == true) {
      if (LogicHelperFunctions.isGameOver() == false) {
        LogicHelperFunctions.toggleTurn()
        val serve = LogicHelperFunctions.getStatusData()
        Ok(serve)
      }
      else {
        val serve = LogicHelperFunctions.getStatusData()
        postGameOver()
        Ok(serve)
      }
    }
    else
      Ok("Invalid nodeId sent!. You can replay the move by making another PUT / request to http://localhost:9000/game/:playerNo/:nodeId with valid parameters!")
  }*/

  /*def getNodes(): Action[AnyContent] = Action {
      Ok(Json.toJson(LogicHelperFunctions.getNodeIds()))
    }*/
}