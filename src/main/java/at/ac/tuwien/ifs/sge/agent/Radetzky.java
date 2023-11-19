package at.ac.tuwien.ifs.sge.agent;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    public static final double DEFAULT_EXPLOITATION_CONSTANT = Math.sqrt(2);
    private static final int DEFAULT_SIMULATION_DEPTH = 20;
    private static final int executionTime = 1000;
    private static final Comparator<GameNode> gameMcTreeSelectionComparator = Comparator.comparingDouble(t -> t.heuristic(DEFAULT_EXPLOITATION_CONSTANT));
    private Future<?> mctsIterationFuture;
    private volatile boolean isRunning;
    private final UnitDirectory unitDirectory;
    private final HashSet<EmpireEvent> concurrentEvents = new HashSet<>();

    public static void main(String[] args) {
        var playerId = getPlayerIdFromArgs(args);
        var playerName = getPlayerNameFromArgs(args);
        var agent = new Radetzky(playerId, playerName, -2);
        agent.start();
    }

    public Radetzky(int playerId, String playerName, int logLevel) {
        super(Empire.class, playerId, playerName, logLevel);
        unitDirectory = new UnitDirectory(playerId);
    }

    @Override
    public void startPlaying() {
        isRunning = true;
        mctsIterationFuture = pool.submit(this::playSimulation);
        // new Thread(this::playSimulation).start();
    }

    @Override
    protected void onGameUpdate(EmpireEvent action, ActionResult result) {
        unitDirectory.onGameUpdate(game, action, log);
        synchronized (concurrentEvents) {
            if (concurrentEvents.remove(action)) {
                log.info("removing from concurrentEvents");
            }
        }
        //unitDirectory.print(log);
    }

    @Override
    protected void onActionRejected(EmpireEvent action) {
        log.error("Rejected " + action.getClass().getSimpleName() + " action " + action);
    }

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 2;
    }

    @Override
    protected void initializeThreadPool() {
        log.info("using " + getMinimumNumberOfThreads() + " threads");
        pool = Executors.newFixedThreadPool(getMinimumNumberOfThreads());
    }

    @Override
    public void shutdown() {
        isRunning = false;
        mctsIterationFuture.cancel(true);
    }

    private double getDiscoveredTilesRatio(Empire gameState) {
        var board = gameState.getBoard();
        var size = board.getMapSize();
        int discovered = 0;
        int undiscovered = 0;
        try {
            for (int x = 0; x < size.getWidth(); x += 3) {
                for (int y = 0; y < size.getHeight(); y += 3) {
                    if (board.getTile(x, y) == null) undiscovered++;
                    else discovered++;
                }
            }
        } catch (EmpireMapException e) {
            log.error(e);
        }
        return ((double) discovered) / (undiscovered + discovered);
    }

    private void playSimulation() {
        log.info("play simulation");
        Random random = new Random();
        EmpireEvent lastAction = null;
        boolean firstProd = true;
        while (isRunning) {
            try {
                var simulatedGameState = (Empire) game.copy();
                for (Map.Entry<Position, EmpireCity> entry : simulatedGameState.getCitiesByPosition().entrySet()) {
                    var city = entry.getValue();
                    log.info("city " + city + " in state " + city.getState());
                }
                if (lastAction != null) {
                    log.info("-------------------------------------------------------------");
                    log.info("-------------------------------------------------------------");
                    log.info("last action " + lastAction.getClass().getSimpleName() + " " + lastAction);
                } else log.info("last action == null");
                var unitHeuristics = unitDirectory.getHeuristics();
                if (lastAction != null && simulatedGameState.isValidAction(lastAction, playerId)) {
                    simulatedGameState.scheduleActionEvent(new GameActionEvent<>(playerId, lastAction, simulatedGameState.getGameClock().getGameTimeMs() + 1));
                    unitHeuristics.apply(simulatedGameState, lastAction);
                }

                try {
                    simulatedGameState.advance(executionTime);
                } catch (ActionException e) {

                    log.info(e.getMessage());
                    var cause = e.getCause();
                    if (cause != null) log.info(cause.getMessage());
                    Thread.sleep(1000);
                    continue;
                }
                lastAction = null;
                unitHeuristics.advance(simulatedGameState);

                var discoveredTilesRatio = getDiscoveredTilesRatio(simulatedGameState);
                log.info("discoveredTilesRatio " + discoveredTilesRatio);

                var root = new GameNode(unitHeuristics, executionTime, playerId, simulatedGameState, null, discoveredTilesRatio);

                var iterations = 0;
                var now = System.currentTimeMillis();
                var timeOfNextDecision = now + executionTime;

                while (System.currentTimeMillis() < timeOfNextDecision) {

                    // Select the best from the children according to the upper confidence bound
                    var tree = root.selection(gameMcTreeSelectionComparator);

                    // Expand the selected node by one action
                    tree.expand(discoveredTilesRatio);

                    // Simulate until the simulation depth is reached and determine winners
                    var winners = tree.simulate(random, DEFAULT_SIMULATION_DEPTH, timeOfNextDecision, log, discoveredTilesRatio);

                    // Back propagate the wins of the agent
                    tree.backPropagation(winners);

                    iterations++;
                }

                if (root.isLeaf()) {
                    log.info("Could not find a move! Doing nothing...");
                } else {
                    log.info("Iterations: " + iterations);
                    root.print(log);
                    var mostVisitedChild = root.getMostVisitedChild();
                    var bestAction = mostVisitedChild.getResponsibleAction();
                    if (bestAction instanceof ProductionStartOrder) {
                        if (!firstProd) bestAction = null;
                        firstProd = false;
                    }
                    if (bestAction != null) {
                        log.info("Determined next action: " + bestAction.getClass().getSimpleName() + " " + bestAction);

                        synchronized (concurrentEvents) {
                            if (concurrentEvents.contains(bestAction)) {
                                log.info("best action is already performed, skipping");
                                continue;
                            }
                            concurrentEvents.add(bestAction);
                        }
                        if (game.isValidAction(bestAction)) sendAction(bestAction, System.currentTimeMillis() + 50);
                        else bestAction = null;
                    } else {
                        log.info("Determined next action: null");
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
            Thread.yield();
        }
        log.info("stopped playing");
    }
}
