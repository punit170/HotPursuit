package models

import com.typesafe.config.{Config, ConfigFactory}
import play.api.Logger
import scala.collection.mutable

//Components needed to de-serialize graphs(txt-formatted)
//Note: To serialize graphs in txt format, add the file '/app/assets/AddTo_NetGameSim/NGStoText.scala' to NetGameSim (also see comments in NGStoText.scala)
object NetGameSim{
  import scala.io.Source
  import org.apache.hadoop.fs.{FileSystem, Path}
  import java.net.URI

  val logger: Logger = Logger(NetGameSim.getClass)

  trait NetGraphComponent extends Serializable

  case class NodeObject(id: Int, children: Int, props: Int, currentDepth: Int = 1, propValueRange: Int, maxDepth: Int,
                        maxBranchingFactor: Int, maxProperties: Int, storedValue: Double, valuableData: Boolean = false) extends NetGraphComponent

  case class Action(actionType: Int, fromNode: NodeObject, toNode: NodeObject, fromId: Int, toId: Int, resultingValue: Option[Int], cost: Double) extends NetGraphComponent

  //new NetGraph class
  case class NetGraph(nodes: List[NodeObject], edges: List[Action], initNode: NodeObject)

  //function to convert a string to NodeObject
  def stringToNodeObject(strObj: String): NodeObject = {
    val regexPattern = """-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.E-]+,\s?(?:true|false)""".r
    val nodeFields: Array[String] = regexPattern.findFirstIn(strObj).get.split(',')
    if (nodeFields.length != 10) {
      println(nodeFields.mkString("Array(", ", ", ")"))
      logger.info(s"NodeStr: $strObj doesn't have 10 fields!")
      throw new Exception(s"NodeStr: $strObj doesn't have 10 fields!")

    }
    /*NodeObject(id: Int, children: Int, props: Int, currentDepth: Int = 1, propValueRange:Int,
     maxDepth:Int, maxBranchingFactor:Int, maxProperties:Int, storedValue: Double)*/

    val id = nodeFields(0).toInt
    val children = nodeFields(1).toInt
    val props = nodeFields(2).toInt
    val currentDepth = nodeFields(3).toInt
    val propValueRange = nodeFields(4).toInt
    val maxDepth = nodeFields(5).toInt
    val maxBranchingFactor = nodeFields(6).toInt
    val maxProperties = nodeFields(7).toInt
    val storedValue = nodeFields(8).toDouble
    val valuableData = nodeFields(9).toBoolean

    NodeObject(id, children, props, currentDepth, propValueRange, maxDepth, maxBranchingFactor, maxProperties, storedValue, valuableData)
  }

  //function to convert a string to Action
  def stringToActionObject(actionstr: String): Action = {
    val nodepattern = """NodeObject\([^)]+\),""".r
    val fromToNodesArr = nodepattern.findAllIn(actionstr).toArray
    val newactionstr = nodepattern.replaceAllIn(actionstr, "")
    val actionparts = newactionstr.substring(7, newactionstr.length - 1).split(',')

    /*case class Action(actionType: Int, fromNode: NodeObject, toNode: NodeObject,
      fromId: Int, toId: Int, resultingValue: Option[Int], cost: Double)*/
    val actionType = actionparts(0).toInt
    val fromNode = stringToNodeObject(fromToNodesArr(0))
    val toNode = stringToNodeObject(fromToNodesArr(1))
    val fromId = actionparts(1).toInt
    val toId = actionparts(2).toInt
    val resultingValue: Option[Int] = {
      if (actionparts(3) == "None") None
      else if (actionparts(3).startsWith("Some")) Some(actionparts(3).substring(5, actionparts(3).length - 1).toInt)
      else None
    }
    val cost = actionparts(4).toDouble
    val action = Action(actionType, fromNode, toNode, fromId, toId, resultingValue, cost)
    action
  }

  //deserialize NetGraph from string read from graph's text file
  def deserializeGraph(graph_string: String): NetGraph = {

    val tempStrArr = graph_string.split(":")

    val allGraphNodesAsString = tempStrArr(0).substring(5, tempStrArr(0).length - 1)
    val allGraphActionsAsString = tempStrArr(1).substring(5, tempStrArr(1).length - 1)

    val allGraphNodesAsStringList = """NodeObject\([^)]+\)""".r.findAllIn(allGraphNodesAsString).toList
    val allGraphActionsAsStringList = """Action\([\d]+,\s?NodeObject\([^)]+\),\s?NodeObject\([^)]+\),\s?[\d]+,\s?[\d]+,\s?(?:None|Some\([\d]+\)),\s?[0-9.]+\)""".r.findAllIn(allGraphActionsAsString).toList


    val allGraphNodes: List[NodeObject] = allGraphNodesAsStringList.map(nodeStr => stringToNodeObject(nodeStr))
    val allGraphActions: List[Action] = allGraphActionsAsStringList.map(actionStr => stringToActionObject(actionStr))
    val initNode = allGraphNodes.find(n => n.id == 0).getOrElse(throw new Exception("NodeObject with id == 0 not found in the loaded graph nodes!"))

    NetGraph(allGraphNodes, allGraphActions, initNode)
  }

  //function to load NetGraph from a text file
  def loadGraph(dir: String, fileName: String, masterURL: String): NetGraph = {
    val filePath = s"$dir$fileName"
    if (masterURL.startsWith("hdfs://") || masterURL.startsWith("s3://")) {
      val conf = new org.apache.hadoop.conf.Configuration()
      val fs = FileSystem.get(new URI(masterURL), conf)
      val hdfsFilePath = new Path(filePath)

      if (!fs.exists(hdfsFilePath)) {
        logger.info(s"File does not exist at $filePath.")
        throw new Exception(s"File does not exist at $filePath.")
      }
      else {
        val source = Source.fromInputStream(fs.open(hdfsFilePath))
        val content = source.mkString
        source.close()
        deserializeGraph(content)
      }
    }
    else if (masterURL == "local" || masterURL == "file:///") {
      val source = Source.fromFile(filePath)
      val content = source.mkString
      source.close()
      //      println(content)
      deserializeGraph(content)
    }
    else {
      logger.info("masterURL should be set to either on local path, hdfs localhost path or s3 bucket path")
      throw new Exception("masterURL should be set to either on local path, hdfs localhost path or s3 bucket path")
    }
  }

  //generate confidence scores for nodes in perturbed graphs
  def genConfidenceScores(nGraph: NetGraph, pGraph: NetGraph): Map[NodeObject, Float] = {
    val confidenceScoreMap = mutable.Map.empty[NodeObject, Float]
    pGraph.nodes.foreach(pNode => {
      val nodeMatch = nGraph.nodes.find(nNode => nNode.id == pNode.id)
      nodeMatch match {
        case Some(nNode) =>
          val nNodeEdges: List[Action] = nGraph.edges.filter(ac => (ac.toNode.id == nNode.id) || (ac.fromNode.id == nNode.id))
          val pNodeEdges: List[Action] = pGraph.edges.filter(ac => (ac.toNode.id == pNode.id) || (ac.fromNode.id == pNode.id))
          confidenceScoreMap(pNode) = (pNodeEdges.intersect(nNodeEdges).length + {if (pNode == nNode) 1 else 0}) / (pNodeEdges.length + 1).toFloat
        case None => confidenceScoreMap(pNode) = 0.0f
      }
    })
    confidenceScoreMap.toMap
  }

}


//contains backend logic for this resftul service. Used by controller- HomeController
object LogicHelperFunctions {
  import NetGameSim._
  import play.api.libs.json._
  import scala.util.Random

  val logger: Logger = Logger(LogicHelperFunctions.getClass)
  val props: Config = ConfigFactory.load()
  val envProps: Config = props.getConfig("local")

  //environment table for this game- contains game variable bindings
  /*
  * gameOn- represents state of the game - on('true') or off('false')
  * player1- represents what actor player1 is- 'policeman' or 'thief'
  * player2- represents what actor player2 is- 'thief' or 'policeman'
  * arePlayersSet- used to check if player1 and player2 have been selected as 'policeman'/'thief'
  * currentTurn- represents whose turn it is to play
  */
  val environmentTable: mutable.Map[String, String] = mutable.Map("gameOn" -> "false", "arePlayersSet" -> "false", "player1" -> "null", "player2" -> "null", "currentTurn" -> "null", "winner" -> "null")

  //table containing current-position nodes and neighbouring nodes of 'policeman' and 'thief'
  val gameTable: mutable.Map[String, (NodeObject, List[NodeObject])] = mutable.Map("policeman" -> null, "thief" -> null)

  //function to check if clients(players) selected player1 as the 'policeman'
  def isPlayer1TheCop() :Boolean =  {environmentTable("player1") == "policeman"}

  //variables for graphs
  lazy val netGraph: NetGraph = loadGraph(fileName = envProps.getString("originalGraph"), dir = envProps.getString("dir"), masterURL = envProps.getString("masterURL"))
  lazy val perturbedGraph: NetGraph = loadGraph(fileName = envProps.getString("perturbedGraph"), dir = envProps.getString("dir"), masterURL = envProps.getString("masterURL"))
  //map for perturbed graph with key: nodeId, value: confidence score
  lazy val confScoreMap: Map[Int, Float] = initializer()

  //getters and setters
  def getCurrentTurnPlayer(): String = environmentTable("currentTurn")
  def getWinner(): String = environmentTable("winner")
  def isGameOn(): String = environmentTable("gameOn")
  def setGame(setBool: Boolean): Unit = {
    environmentTable("gameOn") = setBool.toString
  }
  def setPlayers(player1: String, player2: String): Unit = {
    environmentTable("player1") = player1
    environmentTable("player2") = player2
    environmentTable("arePlayersSet") = "true"
  }
  def setCurrentTurn(player: String): Unit = {
    assert(player == "player1" || player == "player2" || player == null)
    environmentTable("currentTurn") = player
  }


  //function to generate confidence scores
  def initializer(): Map[Int, Float] = {
    logger.info("Loading Game.......")
    val nGraph = netGraph
    logger.info(s"${envProps.getString("originalGraph")} graph loaded successfully!")
    val pGraph = perturbedGraph
    logger.info(s"${envProps.getString("perturbedGraph")} graph loaded successfully!")

    val confScoreMap = genConfidenceScores(nGraph, pGraph)
    confScoreMap.map(ele => (ele._1.id, ele._2))
  }

  //function to toggle 'currentTurn' in the environment table
  def toggleTurn(): Unit = {
    if (environmentTable("currentTurn") == "player1")
      environmentTable("currentTurn") = "player2"
    else if (environmentTable("currentTurn") == "player2")
      environmentTable("currentTurn") = "player1"
    else
      throw new Exception("dev error: currentTurn should be either = \"player1\" or \"player2\"! ")
  }

  //internal function to fetch adjacent outgoing nodes of a node in the perturbed graph
  private def getOutNodes(node: NodeObject): List[NodeObject] = {
    perturbedGraph.edges.filter(ac => ac.fromNode.id == node.id).map(_.toNode)
  }

  //function to initialize players' position at the start of the game
  def initPlayerPositions(): Unit = {
    assert(gameTable("policeman") == null)
    assert(gameTable("thief") == null)

    val numNodesInPGraph = perturbedGraph.nodes.length
    val policemanInitNode = perturbedGraph.nodes(Random.nextInt(numNodesInPGraph))
    val thiefInitNode = perturbedGraph.nodes(Random.nextInt(numNodesInPGraph))

    val policemanOutNodes = getOutNodes(policemanInitNode)
    val thiefOutNodes = getOutNodes(thiefInitNode)

    gameTable("policeman") = (policemanInitNode, policemanOutNodes)
    gameTable("thief") = (thiefInitNode, thiefOutNodes)
  }

  //internal function to check if game is ready to be played
  private def checkGameVars(): String = {
    if(environmentTable("gameOn") == "false")
      return "Game if off. Make a GET/ request on URL http://localhost:9000/startGame to start the game"
    if (environmentTable("arePlayersSet") == "false")
      return "Game has been turned on, but, players are not set. Make a PUT/ request on URL http://localhost:9000/game/selectPlayers along with json data {\"player1\":\"policeman\"(or \"thief\"), \"player2\":\"thief\"(or \"policeman\")} to start the game"
    if(environmentTable("currentTurn") == "null")
      "dev error: game is on, players are set, but currentTurn not set!"
    else
      "OK"
  }

  //function to help reset the game when completed or stopped
  def resetEnvTables(): Unit = {
    environmentTable("gameOn") = "false"
    environmentTable("arePlayersSet") = "false"
    environmentTable("player1") = "null"
    environmentTable("player2") = "null"
    environmentTable("currentTurn") = "null"
    environmentTable("winner") = "null"
    gameTable("policeman") = null
    gameTable("thief") = null
  }

  //function to move 'player' to 'nodeId'
  //player- should be either 1 or 2
  //nodeId- should be a valid outgoing node for the player
  def movePlayer(player: Int, nodeId: Int): Boolean = {
    assert(checkGameVars() == "OK")
    val currPlayerType: String =  environmentTable("player".concat(player.toString))
    val currPlayerNeighboursNodes: List[NodeObject] = gameTable(currPlayerType)._2
    val currPlayerNeighboursNodeIds: List[Int] = currPlayerNeighboursNodes.map(_.id)

    if(currPlayerNeighboursNodeIds.contains(nodeId)){
      val newNode = currPlayerNeighboursNodes.find(n => n.id == nodeId).get
      val newOutNodes = getOutNodes(newNode)
      gameTable(currPlayerType) = (newNode, newOutNodes)
      true
    }
    else
      false
  }

  //function to check if a player won and the game is over
  def isGameOver(): Boolean  = {
//    println(s"GAME TABLE: $gameTable")
    if (gameTable("policeman")._1 == gameTable("thief")._1){
      environmentTable("winner") = {if (isPlayer1TheCop()) "player1" else "player2"}
      true
    }
    else if (gameTable("thief")._1.valuableData) {
      environmentTable("winner") = {if (isPlayer1TheCop()) "player2" else "player1"}
      true
    }
    else if (gameTable("policeman")._2.isEmpty) {
      environmentTable("winner") = {if (isPlayer1TheCop())"player2" else "player1"}
      true
    }
    else if (gameTable("thief")._2.isEmpty) {
      environmentTable("winner") = {if (isPlayer1TheCop()) "player1" else "player2"}
      true
    }
    else if(environmentTable("winner")!="null")
      true
    else
      false
  }

  //function to get fetch information about player positions and their possible next moves
  def getStatusData(): JsValue = {

    val player1Type = environmentTable("player1")
    val player2Type = environmentTable("player2")

    val player1CurrNodeId = gameTable(player1Type)._1.id
    val player1CurrOutNodesWithConfScore = gameTable(player1Type)._2.map(n => (n.id, confScoreMap(n.id)))

    val player2CurrNodeId = gameTable(player2Type)._1.id
    val player2CurrOutNodesWithConfScore = gameTable(player2Type)._2.map(n => (n.id, confScoreMap(n.id)))

    val data: JsValue = Json.parse(
      s"""
      {
        "gameOn" : ${LogicHelperFunctions.isGameOn()},
        "currentTurn" : ${LogicHelperFunctions.environmentTable("currentTurn").last},
        "player1" : {
          "currPos" : $player1CurrNodeId,
          "outNodeIds" : [${player1CurrOutNodesWithConfScore.map { case (x, y) => s"[$x, $y]" }.mkString(", ")}]
        },
        "player2" : {
          "currPos" : $player2CurrNodeId,
          "outNodeIds" : [${player2CurrOutNodesWithConfScore.map { case (x, y) => s"[$x, $y]" }.mkString(", ")}]
        },
        "winner" : "${LogicHelperFunctions.environmentTable("winner")}"
      }
      """)


    data
  }


  //functions for testing purpose
  /*def getNodeIds(): List[Int] = {
      val graph = loadGraph(fileName = envProps.getString("originalGraph"), dir = envProps.getString("dir"), masterURL = envProps.getString("masterURL"))
      logger.info(s"${envProps.getString("originalGraph")} graph loaded successfully!")
      val og = graph.nodes.map(_.id)
      og
    }

    def getStatus(): String = {
      /*
        status:
          player1(policeman):
            currentNode- 1
            outNodes:
              -> 2 conf=0.5
              ->3 conf=1.0
          player2(thief):
                currentNode- 1
                outNodes:
                  -> 2 conf=0.5
                  ->3 conf=1.0
      */
      val player1Type = environmentTable("player1")
      val player2Type = environmentTable("player2")

      val player1CurrNodeId = gameTable(player1Type)._1.id
      val player1CurrOutNodesWithConfScore = gameTable(player1Type)._2.map(n => (n.id, confScoreMap(n.id)))

      val player2CurrNodeId = gameTable(player2Type)._1.id
      val player2CurrOutNodesWithConfScore = gameTable(player2Type)._2.map(n => (n.id, confScoreMap(n.id)))

      val status = "status:\n\t" +
        s"player1($player1Type):\n\t\t" +
        s"currentPosition- $player1CurrNodeId\n\t\t" +
        s"outNodeIds: \n\t\t\t${player1CurrOutNodesWithConfScore.foldLeft(""){case (acc, (id, cs)) => acc + (id.toString + " conf=" + cs.toString + "\n\t\t\t")}}\n\t" +
        s"player2($player2Type):\n\t\t" +
        s"currentPosition- $player2CurrNodeId\n" +
        s"outNodeIds: \n\t\t\t${player2CurrOutNodesWithConfScore.foldLeft(""){case (acc, (id, cs)) => acc + (id.toString + " conf=" + cs.toString + "\n\t\t\t")}}"
      return status
    }
    */

}