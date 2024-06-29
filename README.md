# HotPursuit

An implementation of a variant of the Policeman/Thief graph game using Scala/Play 3 Framework.

## Project Description
<div style="text-align: justify;">
   
This project aims to develop a RESTful service that allows clients to play a variant of the [Policeman Thief game](https://en.wikipedia.org/wiki/Pursuit%E2%80%93evasion) by sending HTTP requests to the service. The game is based on graphs, where nodes represent locations in the game. A Policeman and a Thief are placed on different nodes, with the objective for the Policeman to catch the Thief, while the Thief aims to reach a node with valuable data.

</div>

## Requirements

- Install [Simple Build Toolkit (SBT) MSI Installer](https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Windows.html). Ensure you can create, compile, and run Java and Scala programs.
- Oracle OpenJDK 17.0.8 (compatible with Play 3)
- Scala 2.13.12
- sbt version: 1.9.6 (defined in `project/build.properties`)
- Other dependencies specified in `build.sbt`
- IntelliJ IDEA Ultimate (recommended IDE for development)

## How to install and run the project
   
1. Clone the project. The base directory should be named "HotPursuit".
   
> [!IMPORTANT]
> **Generating Input Graph Files**: 
> - This project uses case classes from [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim) to define nodes and edges of graphs.
> - NetGameSim generates ".ngs" and    ".ngs.perturbed" binary files for original and perturbed graphs. To use these files with this project, convert them to ".txt" and ".txt.perturbed" formats using additional code provided in this repository's path: `/app/assets/AddTo_NetGameSim/NGStoText.scala`.
> - Follow the instructions in the comments of `NGStoText.scala` to integrate this code into NetGameSim's `base directory/src/main/scala/` directory. 

---

2. Open the `application.conf` file located in the `/conf/` directory and configure the following settings:
   ```md
   - `dir`: Path to the directory where all graph files (`someOriginalGraph.txt`, `somePerturbedGraph.txt.perturbed`) are stored. For ease, keep it as `./app/assets/` and place the graph files inside the assets directory.
   - `originalGraph`: File name of the original graph (must have `.txt` extension).
   - `perturbedGraph`: File name of the perturbed graph (must have `.txt.perturbed` extension).
   - `masterURL`: Determines the file system. Set to `"hdfs://localhost:<port>"`, `"local"`, or `"s3://<your_bucket_name>"`, depending on the file system being used.
   ```
   
---

3. Save the graph files with `.txt` and `.txt.perturbed` extensions in the `dir` directory.<br>

---

4. If running on a local machine:
> [!Tip]
> Set paths under local scope in application.conf if the underlying graph of the game needs to be changed (check out step 2)

   - **4.1** From the terminal navigate to the base directory and run the following commands:
   ```bash
   sbt clean compile
   sbt run
   ```
   This should start the server.
     
   - **4.2** Now the game can be played through any utility like cURL or API like Postman. 

---

5. **If deploying this service on Amazon EC2 and accessing it over the internet**
     - **5.1** Select an EC2 instance with at least 4GB RAM. Make sure it has a public IP address. Configure it by installing Java JDK 17.0.8 and sbt 1.9.6/1.9.7. Also download packages for unzip and git functionalities.
    
     - **5.2** Clone this [HotPursuit](https://github.com/punit170/HotPursuit) git repository.
    
     - **5.3** Navigate inside the cloned directory and run the command `sbt clean compile` from the terminal to compile the service.
    
     - **5.4** Run `sbt run` command to start the service.
    
     - **5.5** Create an inbound rule for the EC2 instance and make it available for your selected port(by default 9000) to listen to requests from over the internet.
    
     - **5.6** Now that game can be played by making requests from a local machine to the ***ec2Instance*-publicIP:9000** <br>

## HomeController, LogicHelperFunction, routes, and NGStoText
   
#### 1. HomeController.scala
<div style="text-align: justify;">
This file, `HomeController.scala`, serves as the controller for the project. It defines the behavior of the service, handling incoming requests and generating appropriate responses. It interfaces with `LogicHelperFunctions.scala` for executing business logic. Detailed explanations for each component can be found as comments within `HomeController.scala`.
</div>

#### 2. LogicHelperFunctions.scala
<div style="text-align: justify;">
`LogicHelperFunctions.scala` contains crucial helper functions that implement various logical operations required by the application. These functions include deserialization, confidence-score generation algorithms, game start procedures, player selection mechanisms, and player movement logic. They are essential for the overall functionality of the project.
</div>

#### 3. routes
<div style="text-align: justify;">
Routes for this RESTful service are defined in the `routes` file located under the `conf` directory.
</div>

> [!NOTE]
The game employs the following routes, which are part of the URL structure:
```md
- `GET    /startGame`                   - Initiates the game.
- `GET    /game/instructions`           - Displays game instructions.
- `GET    /stopGame`                    - Stops the game at any point.
- `PUT    /game/selectplayers`          - Selects actors for player 1 and player 2. This request should be made after the game has started. The request body should include JSON data as `{"player1":"policeman"}` or `{"player1":"thief"}`, and `{"player2":"thief"}` or `{"player2":"policeman"}`.
- `PUT    /game/move/:playerNo/:nodeId` - Moves the player identified by `:playerNo` to the node specified by `:nodeId`. This request should be made after the game has started and players have been selected. Example: `PUT /move/1/10` moves `player1` to node `10`.
- `GET    /game/getStatusData`          - Retrieves information about a player's own location, the opponent's location nodesIds, and adjacent nodesIds.
- `GET    /auto`                        - Initiates an automatic gameplay simulation that runs until the game ends.
```

#### 4. NGStoText.scala
>`app/assets/AddTo_NetGameSim/NGStoText.scala`

`NGStoText.scala` is a file located within the `AddTo_NetGameSim` directory under `assets`. Its purpose is to facilitate the conversion of output files from NetGameSim into text format. This is achieved through specific code that transforms `.ngs` and `.ngs.perturbed` binary files generated by NetGameSim into `.txt` and `.txt.perturbed` formats, respectively.

###### Generating Input Graph Files 
> - This project uses case classes from [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim) to define nodes and edges of graphs.
> - NetGameSim generates ".ngs" and    ".ngs.perturbed" binary files for original and perturbed graphs. To use these files with this project, convert them to ".txt" and ".txt.perturbed" formats using additional code provided in this repository's path: `/app/assets/AddTo_NetGameSim/NGStoText.scala`.
> - Follow the instructions in the comments of `NGStoText.scala` to integrate this code into NetGameSim's `base directory/src/main/scala/` directory.

<br> 

## Tasks:

#### 1. <ins>Implementation of Confidence-Score Generation Algorithm</ins>
A pair of graphs is generated from NetGameSim:
1. **Original Graph**: Referenced as `originalNetGraph` in the code.
2. **Perturbed Graph**: Referenced as `perturbedNetGraph` in the code.

The first step of this project involves reading these graphs. To assess the degree to which perturbed nodes match their original counterparts, confidence scores are calculated for nodes in the perturbed graph. The algorithm takes in both graphs, computes confidence scores for each node in the perturbed graph, and outputs a mapping with key: `nodeId` and value: confidence score.

#### 2. <ins>Construction of Routes for HTTP Requests</ins>
Routes are defined to specify requests for using this RESTful service. In this project, routes are configured using the Play framework, and they are defined within the **conf** directory in a file named **routes**. For more information on the structure and anatomy of Play Framework applications, refer to [Play Framework Anatomy](https://www.playframework.com/documentation/3.0.x/Anatomy).

#### 3. <ins>Implementation of Backend Logic</ins>
Backend logic functions are implemented to define the operational behavior of the game. Following the MVC (Model-View-Controller) architecture of Play Framework, these logic functions reside in the models module of the application.

#### 4. <ins>Construction of Controllers</ins>
Controllers define the behavior of the service and specify responses to requests. In this project, controllers are implemented under the controllers module of the application.

>[!NOTE]
> This service is designed as a RESTful API and does not include web page views as it focuses on backend logic and HTTP API interaction.<br>


## Errors and their causes

The service responds with the following error messages (self-explanatory) for corresponding situations:

1. **"The game has not started, and so players cannot be set. To start the game make a GET / request to http://localhost:9000/startGame"**
   - This error occurs if a select player request is made before starting a game.

2. **"Players already set! Cannot reset them, while the game is on!"**
   - This error is triggered if another select player request is made when the game is already in progress and players have already been selected.

3. **"PlayerNo should be either 1 or 2. You can replay the move by making another PUT / request to http://localhost:9000/game/:playerNo/:nodeId with valid parameters!"**
   - This error message appears when an invalid player number is provided in the move player URL (`PUT /game/:playerNo/:nodeId`).

4. **"Player $playerNo cannot make two moves consecutively!"**
   - This error is shown if consecutive move requests are made for the same player without an opponent's move in between.

5. **"Invalid move! Check nodeId / playerNo!"**
   - This error indicates an invalid `nodeId` or `playerNo` provided in the move player URL (`PUT /game/:playerNo/:nodeId`).<br>


## Scope for Improvement

1. **Integration with NetGameSim:**
   - Enhance the game by integrating with NetGameSim to dynamically generate random graphs each time the game starts. This would provide diverse and unpredictable scenarios for players.

2. **Accommodating Multiple Players:**
   - Extend the game's functionality to accommodate multiple 'policeman' and 'thief' players within a single game session. This addition would introduce complexity and strategic depth, enhancing the multiplayer experience.

   
## YouTube link: [Project Demonstration + aws ec2 deployment](https://www.youtube.com/watch?v=vagaZzr5I2w)

## Need Help?

Reach out to me!
**Email:** [punit.malpani@gmail.com](mailto:punit.malpani@gmail.com)
