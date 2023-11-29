package at.ac.tuwien.ifs.sge.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import at.ac.tuwien.ifs.sge.agent.discoveredBoard.DiscoveredBoard;
import at.ac.tuwien.ifs.sge.agent.discoveredBoard.EnemyDiscoveredBoard;
import at.ac.tuwien.ifs.sge.agent.discoveredBoard.RadetzkyDiscoveredBoard;
import at.ac.tuwien.ifs.sge.agent.unitHeuristics.EnemyUnitHeuristics;
import at.ac.tuwien.ifs.sge.agent.unitHeuristics.UnitDirectory;
import at.ac.tuwien.ifs.sge.agent.unitHeuristics.UnitHeuristics;
import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.CombatHitAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.MovementAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.ProductionAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    public static final float DEFAULT_EXPLOITATION_CONSTANT = (float) Math.sqrt(2);
    private static final int DEFAULT_SIMULATION_DEPTH = 30;
    private static final int executionTime = 2000;
    private static final int simulationTimeStep = 1000;
    private static final int simulationThreads = 6;
    private final Future<?>[] mctsIterationFutures = new Future[simulationThreads];
    private volatile boolean isRunning;
    private UnitDirectory unitDirectory;
    private RadetzkyDiscoveredBoard radetzkyDiscoveredBoard;
    private UnitHeuristics[] unitHeuristics;
    private DiscoveredBoard[] gameBoards;
    private int numberOfUnits = 1;

    public static void main(String[] args) {
        var playerId = getPlayerIdFromArgs(args);
        var playerName = getPlayerNameFromArgs(args);
        var agent = new Radetzky(playerId, playerName, -2);
        agent.start();
    }

    public Radetzky(int playerId, String playerName, int logLevel) {
        super(Empire.class, playerId, playerName, logLevel);
    }

    @Override
    public void startPlaying() {
        isRunning = true;
        unitDirectory = new UnitDirectory(playerId);
        initHeuristics();
        mctsIterationFutures[0] = pool.submit(() -> {
            for (int i = 1; i < simulationThreads; i++) {
                final int id = i;
                mctsIterationFutures[i] = pool.submit(() -> playSimulation(id));
                try {
                    Thread.sleep(executionTime / simulationThreads);
                } catch (InterruptedException ignored) {}
            }
            playSimulation(0);
        });
        var game = getGame();
        for (EmpireEvent action : game.getPossibleActions(playerId)) {
            if (action instanceof ProductionStartOrder productionStartOrder) {
                if (productionStartOrder.getUnitTypeId() == 2) {
                    sendAction(action, System.currentTimeMillis() + 50);
                    return;
                }
            }
        }
    }

    private void initHeuristics() {
        var game = copyGame();
        var numberOfPlayers = game.getNumberOfPlayers();
        gameBoards = new DiscoveredBoard[numberOfPlayers];
        unitHeuristics = new UnitHeuristics[numberOfPlayers];
        for (int i = 0; i < numberOfPlayers; i++) {
            if (i == playerId) {
                gameBoards[i] = radetzkyDiscoveredBoard = RadetzkyDiscoveredBoard.get(playerId, game);
            } else {
                var enemyBoard = EnemyDiscoveredBoard.get(i, game);
                gameBoards[i] = enemyBoard;
                unitHeuristics[i] = new EnemyUnitHeuristics(i, enemyBoard);
            }
        }
    }

    @Override
    protected void onActionRejected(EmpireEvent action) {
        log.error("Rejected " + action.getClass().getSimpleName() + " action " + action);
    }

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 2 + simulationThreads;
    }

    @Override
    protected void initializeThreadPool() {
        log.info("using " + getMinimumNumberOfThreads() + " threads");
        pool = Executors.newFixedThreadPool(getMinimumNumberOfThreads());
    }

    @Override
    protected void onGameUpdate(HashMap<EmpireEvent, ActionResult> actionsWithResult) {
        var game = getGame();
        for (Map.Entry<EmpireEvent, ActionResult> entry : actionsWithResult.entrySet()) {
            if (!entry.getValue().wasSuccessful()) continue;
            var action = entry.getKey();
            unitDirectory.onGameUpdate(game, action, log);
            for (int i = 0; i < unitHeuristics.length; i++) {
                if (unitHeuristics[i] != null) unitHeuristics[i].apply(game, action);
                gameBoards[i].apply(game, action);
            }

            if (action instanceof ProductionAction productionAction) {
                var cityPosition = productionAction.getCityPosition();
                var city = game.getCity(cityPosition);
                if (city == null) return;
                var newUnit = game.getUnit(productionAction.getUnitId());
                if (newUnit.getPlayerId() != playerId) continue;
                if (numberOfUnits < game.getGameConfiguration().getUnitCap()) sendAction(new ProductionStartOrder(cityPosition, UnitDirectory.getBestCurrentUnitType(radetzkyDiscoveredBoard)), System.currentTimeMillis() + 50);
                numberOfUnits++;
            } else if (action instanceof MovementAction movementAction) {
                var unit = game.getUnit(movementAction.getUnitId());
                if (unit == null || unit.getPlayerId() != playerId) return;
                var destination = movementAction.getDestination();
                var city = game.getCity(destination);
                if (city == null) return;
                if (numberOfUnits < game.getGameConfiguration().getUnitCap()) sendAction(new ProductionStartOrder(destination, UnitDirectory.getBestCurrentUnitType(radetzkyDiscoveredBoard)), System.currentTimeMillis() + 50);
            } else if (action instanceof CombatHitAction combatHitAction) {
                var hitUnit = game.getUnit(combatHitAction.getTargetId());
                if (hitUnit.getPlayerId() != playerId) return;
                if (!hitUnit.isAlive()) {
                    numberOfUnits--;
                    //todo produce unit in some city
                }
            }
        }
    }

    @Override
    public void shutdown() {
        isRunning = false;
        for (int i = 0; i < simulationThreads; i++) {
            mctsIterationFutures[i].cancel(true);
        }
    }

    private final UUID[] unitsTaskedByThreads = new UUID[simulationThreads];

    private UUID[] copyUnitsTaskedByThreads() {
        UUID[] copy = new UUID[simulationThreads];
        System.arraycopy(unitsTaskedByThreads, 0, copy, 0, simulationThreads);
        return copy;
    }

    private void playSimulation(int threadNumber) {
        log.info("play simulation");
        Random random = new Random(0);
        EmpireEvent lastAction = null;
        while (isRunning) {
            try {
                var simulatedGameState = copyGame();
                var unitHeuristics = UnitHeuristics.copy(this.unitHeuristics);
                var gameBoards = DiscoveredBoard.copy(this.gameBoards);
                unitHeuristics[playerId] = unitDirectory.getHeuristics(radetzkyDiscoveredBoard.copy());

                applyLastAction(lastAction, numberOfPlayers, gameBoards, unitHeuristics, simulatedGameState);
                lastAction = null;

                if (!advanceSimulatedGameAndHeuristics(numberOfPlayers, gameBoards, unitHeuristics, simulatedGameState)) continue;

                var root = new GameNode(random, unitHeuristics, simulationTimeStep, playerId, playerId, playerId, simulatedGameState, null, gameBoards);

                var iterations = 0;
                var now = System.currentTimeMillis();
                var timeOfNextDecision = now + executionTime;

                while (System.currentTimeMillis() < timeOfNextDecision) {

                    // Select the best from the children according to the upper confidence bound
                    var tree = root.getBestByHeuristicRecursively(DEFAULT_EXPLOITATION_CONSTANT);

                    // Expand the selected node by one action
                    tree.expand();

                    // Simulate until the simulation depth is reached and determine winners
                    tree.simulate(random, DEFAULT_SIMULATION_DEPTH, timeOfNextDecision, log);

                    iterations++;
                }


                if (root.isLeaf()) {
                    log.info("Could not find a move! Doing nothing...");
                    unitsTaskedByThreads[threadNumber] = null;
                } else {
                    log.info("Iterations: " + iterations);
                    // root.print(log);

                    var taskedUnits = copyUnitsTaskedByThreads();
                    taskedUnits[threadNumber] = null;
                    var bestAction = root.getNonTaskedActionOrNull(taskedUnits);
                    unitsTaskedByThreads[threadNumber] = EmpireEventHelper.getTaskedUnitIdFromEventOrNull(bestAction);
                    if (bestAction != null) {
                        log.info("Determined next action: " + bestAction.getClass().getSimpleName() + " " + bestAction);
                        sendAction(bestAction, System.currentTimeMillis() + 50);
                    } else {
                        log.info("Best to take no action");
                    }
                    lastAction = bestAction;
                }

            } catch (Exception e) {
                log.printStackTrace(e);
                break;
            } catch (OutOfMemoryError e) {
                log.error("OOM!");
                e.printStackTrace();
                break;
            }
        }
        log.info("stopped playing");
    }

    private boolean advanceSimulatedGameAndHeuristics(int numberOfPlayers, DiscoveredBoard[] gameBoards, UnitHeuristics[] unitHeuristics, Empire simulatedGameState) {
        try {
            simulatedGameState.advance(executionTime);
        } catch (ActionException e) {
            log.info(e.getMessage());
            var cause = e.getCause();
            if (cause != null) log.info(cause.getMessage());
            return false;
        }

        for (int i = 0; i < numberOfPlayers; i++) {
            unitHeuristics[i].advance(simulatedGameState, executionTime);
            gameBoards[i].advance(executionTime, simulatedGameState, unitHeuristics[i]);
        }
        return true;
    }

    private void applyLastAction(EmpireEvent lastAction, int numberOfPlayers, DiscoveredBoard[] gameBoards, UnitHeuristics[] unitHeuristics, Empire simulatedGameState) {
        if (lastAction == null || !simulatedGameState.isValidAction(lastAction, playerId)) return;
        simulatedGameState.scheduleActionEvent(new GameActionEvent<>(playerId, lastAction, simulatedGameState.getGameClock().getGameTimeMs() + 1));
        for (int i = 0; i < numberOfPlayers; i++) {
            unitHeuristics[i].apply(simulatedGameState, lastAction);
            gameBoards[i].apply(simulatedGameState, lastAction);
        }
    }
}
