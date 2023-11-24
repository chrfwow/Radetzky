package at.ac.tuwien.ifs.sge.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    public static final float DEFAULT_EXPLOITATION_CONSTANT = (float) Math.sqrt(2);
    private static final int DEFAULT_SIMULATION_DEPTH = 30;
    private static final int executionTime = 2000;
    private static final int simulationTimeStep = 1000;
    private Future<?> mctsIterationFuture;
    private volatile boolean isRunning;
    private final UnitDirectory unitDirectory;

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
            mctsIterationFuture = pool.submit(this::playSimulation);
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
    protected void onGameUpdate(HashMap<EmpireEvent, ActionResult> actionsWithResult) {
        var game = getGame();
        for (Map.Entry<EmpireEvent, ActionResult> entry : actionsWithResult.entrySet()) {
            unitDirectory.onGameUpdate(game, entry.getKey(), log);
        }
    }

    @Override
    public void shutdown() {
        isRunning = false;
        mctsIterationFuture.cancel(true);
    }

    private void playSimulation() {
        log.info("play simulation");
        Random random = new Random(0);
        EmpireEvent lastAction = null;
        while (isRunning) {
            try {
                var simulatedGameState = copyGame();

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
                    continue;
                }
                lastAction = null;
                unitHeuristics.advance(simulatedGameState);

                var discoveredBoard = DiscoveredBoard.get(simulatedGameState);

                var root = new GameNode(unitHeuristics, simulationTimeStep, playerId, simulatedGameState, null, discoveredBoard);

                var iterations = 0;
                var now = System.currentTimeMillis();
                var timeOfNextDecision = now + executionTime;

                while (System.currentTimeMillis() < timeOfNextDecision) {

                    // Select the best from the children according to the upper confidence bound
                    var tree = root.getBestByHeuristicRecursively(DEFAULT_EXPLOITATION_CONSTANT);

                    // Expand the selected node by one action
                    tree.expand();

                    // Simulate until the simulation depth is reached and determine winners
                    var winners = tree.simulate(random, DEFAULT_SIMULATION_DEPTH, timeOfNextDecision, log);

                    // Back propagate the wins of the agent
                    tree.backPropagation(winners);

                    iterations++;
                }

                if (root.isLeaf()) {
                    log.info("Could not find a move! Doing nothing...");
                } else {
                    log.info("Iterations: " + iterations);
                    // root.print(log);
                    var mostVisitedChild = root.getMostVisitedChild();
                    var bestAction = mostVisitedChild.getResponsibleAction();
                    if (bestAction != null) {
                        log.info("Determined next action: " + bestAction.getClass().getSimpleName() + " " + bestAction);
                        sendAction(bestAction, System.currentTimeMillis() + 50);
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
}
