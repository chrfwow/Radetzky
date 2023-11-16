package at.ac.tuwien.ifs.sge.agent;

import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    public static final double DEFAULT_EXPLOITATION_CONSTANT = Math.sqrt(2);
    private static final int DEFAULT_SIMULATION_DEPTH = 20;
    private static final int executionTime = 1000;
    private static final Comparator<GameNode> gameMcTreeSelectionComparator = Comparator.comparingDouble(t -> t.heuristic(DEFAULT_EXPLOITATION_CONSTANT));
    private Future<?> mctsIterationFuture;
    private volatile boolean isRunning;

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
        //mctsIterationFuture = pool.submit(this::playSimulation);
        new Thread(this::playSimulation).start();
    }

    @Override
    protected void onGameUpdate(EmpireEvent action, ActionResult result) {}

    @Override
    protected void onActionRejected(EmpireEvent action) {}

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 2;
    }

    @Override
    protected void initializeThreadPool() {
        // todo remove * 0 + 1
        pool = Executors.newFixedThreadPool(getMinimumNumberOfThreads());
    }

    @Override
    public void shutdown() {
        isRunning = false;
        // mctsIterationFuture.cancel(true);
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
                    discovered++;
                }
            }
        } catch (EmpireMapException e) {
            throw new RuntimeException(e);
        }
        return (double) discovered / (undiscovered + discovered);
    }

    private void playSimulation() {
        Random random = new Random();
        EmpireEvent lastAction = null;
        while (isRunning) {
            try {
                var simulatedGameState = (Empire) game.copy();
                if (lastAction != null && simulatedGameState.isValidAction(lastAction, playerId)) {
                    simulatedGameState.scheduleActionEvent(new GameActionEvent<>(playerId, lastAction, simulatedGameState.getGameClock().getGameTimeMs() + 1));
                }
                simulatedGameState.advance(executionTime);

                var discoveredTilesRatio = getDiscoveredTilesRatio(simulatedGameState);
                var unitHeuristics = new UnitHeuristics(simulatedGameState, playerId);

                var root = new GameNode(executionTime, playerId, simulatedGameState, null, discoveredTilesRatio);

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
                    log.info("iteration " + iterations);
                }

                if (root.isLeaf()) {
                    log.info("Could not find a move! Doing nothing...");
                } else {
                    log.info("Iterations: " + iterations);
                    root.print(log);
                    var mostVisitedChild = root.getMostVisitedChild();
                    var bestAction = mostVisitedChild.getResponsibleAction();
                    if (bestAction != null) {
                        log.info("Determined next action: " + bestAction.getClass().getSimpleName() + " " + bestAction);
                        sendAction(bestAction, System.currentTimeMillis() + 50);
                    } else {
                        log.info("Determined next action: null");
                    }
                    lastAction = bestAction;
                }

            } catch (Exception e) {
                log.printStackTrace(e);
                break;
            } catch (OutOfMemoryError e) {
                break;
            }
        }
        log.info("stopped playing");
    }
}
