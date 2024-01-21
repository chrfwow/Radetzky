package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.MovementAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
//    public class EmpireRunnable implements Runnable {
//        private final EmpireUnit unit;
//        private final boolean runningForever;
//
//        private EmpireUnitState unitState = EmpireUnitState.Idle;
//
//        private double bestReward = 0.0;
//        private EmpireMCTSNode bestNode = null;
//
//        public EmpireRunnable(EmpireUnit unit, boolean runningForever) {
//            this.unit = unit;
//            this.runningForever = runningForever;
//        }
//
//        @Override
//        public void run() {
//            EmpireMCTS mcts = null;
//            Empire gameCopy = null;
//
//            do {
//                if (unitState == EmpireUnitState.Idle) {
//                    bestReward = 0.0;
//
//                    gameCopy = radetzky.copyGame();
//
//                    mcts = new EmpireMCTS(gameCopy, unit);
//                }
//
//                var node = mcts.select();
//
//                if (node == null) {
//                    continue;
//                }
//
//                node = mcts.expand(gameCopy, unit, node);
//
//                if (node == null) {
//                    continue;
//                }
//
//                var reward = mcts.simulate(gameCopy, unit);
//                mcts.backpropagate(node, reward);
//
//                if (reward > bestReward) {
//                    bestReward = reward;
//                    bestNode = node;
//                }
//            } while (runningForever);
//        }
//
//        public EmpireEvent getBestNextAction() {
//            var bestNextAction = bestNode.getAction();
//
//            bestNode = null;
//
//            return bestNextAction;
//        }
//
//        public void setUnitState(EmpireUnitState unitState) {
//            this.unitState = unitState;
//        }
//    }

    private static final int actionExecutionTime = 50;
//    private static final int maximumUnitCount = 10;

    //    private final Radetzky radetzky = this;
    private final Random random = new Random();

    private Map<UUID, EmpireUnitState> unitStates = new HashMap<>();
//    private Map<UUID, EmpireRunnable> mctsRunnablesPerUnit = new HashMap<>();

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
        var units = getGame().getUnitsByPlayer(playerId);

        log.debug("startPlaying");

        for (var unit : units) {
            unitStates.put(unit.getId(), EmpireUnitState.Idle);

//            EmpireRunnable runnable = new EmpireRunnable(unit, true);
//
//            mctsRunnablesPerUnit.putIfAbsent(unit.getId(), runnable);
//
//            pool.execute(runnable);
//
//            log.debug("startPlaying2");
//
//
//            // Starting:
//            var startRunnable = new EmpireRunnable(unit, false);
//
//            startRunnable.run();
//            var startAction = startRunnable.getBestNextAction();
//
//            sendAction(startAction, System.currentTimeMillis() + actionExecutionTime);

            employUnit(unit);
        }
    }

    @Override
    protected void onGameUpdate(HashMap<EmpireEvent, ActionResult> actionsWithResult) {
        for (var entry : actionsWithResult.entrySet()) {
            handleOnGameUpdateAction(entry.getKey(), entry.getValue());
        }

        log.debug("onGameUpdate");
    }

    private void handleOnGameUpdateAction(EmpireEvent action, ActionResult result) {
        log.debug("Action " + action.toString() + " ACCEPTED.");

        if (action instanceof MovementAction movementAction) {
            var unitId = movementAction.getUnitId();
            var unit = getGame().getUnit(unitId);

//            var runnable = mctsRunnablesPerUnit.get(unit);
//            var bestNextAction = runnable.getBestNextAction();
//
//            if (bestNextAction != null) {
//                sendAction(bestNextAction, System.currentTimeMillis() + actionExecutionTime);
//            }
//
//            runnable.setUnitState(EmpireUnitState.Idle);

            employUnit(getGame().getUnit(unitId));
        }

        log.debug("handleOnGameUpdateAction");
    }

    @Override
    protected void onActionRejected(EmpireEvent action) {
        log.debug("Action " + action.toString() + " REJECTED.");

        if (action instanceof MovementStartOrder movementStartOrder) {
            var unitId = movementStartOrder.getUnitId();
            var unit = getGame().getUnit(unitId);

//            var runnable = mctsRunnablesPerUnit.get(unit);
//            var bestNextAction = runnable.getBestNextAction();
//
//            if (bestNextAction != null) {
//                sendAction(bestNextAction, System.currentTimeMillis() + actionExecutionTime);
//            }
//
//            runnable.setUnitState(EmpireUnitState.Idle);

            employUnit(getGame().getUnit(unitId));
        }
    }

    private void employUnit(EmpireUnit unit) {
        if (unit == null) {
            return;
        }

        var unitState = unitStates.getOrDefault(unit.getId(), EmpireUnitState.Idle);

        if (unitState != EmpireUnitState.Idle) {
            return;
        }

//        try {
//            var possibleUnitActions = getGame().getBoard().getPossibleActions(unit);
//            var possibleUnitMoveActions = new ArrayList<MovementStartOrder>();
//
//            for (var action : possibleUnitActions) {
//                if (action instanceof MovementStartOrder movementStartOrder) {
//                    possibleUnitMoveActions.add(movementStartOrder);
//                }
//            }
//
//            var nextAction = Util.selectRandom(possibleUnitMoveActions, random);
//
//            sendAction(nextAction, System.currentTimeMillis() + actionExecutionTime);
//        } catch (EmpireMapException e) {
//            log.error("Actions for unit " + unit.getId() + " could not be retrieved.");
//            log.printStackTrace(e);
//        }


        Empire gameCopy = copyGame();
        EmpireMCTS mcts = new EmpireMCTS(gameCopy, unit);
        int maximumSimulationIterationCount = 25;
        double bestReward = Integer.MIN_VALUE;
        EmpireMCTSNode bestNode = null;

        for (int i = 0; i < maximumSimulationIterationCount; ++i) {
            var node = mcts.select();

            if (node == null) {
                continue;
            }

            node = mcts.expand(gameCopy, unit, node);

            if (node == null) {
                continue;
            }

            var reward = mcts.simulate(gameCopy, unit);

            log.debug("Simulation Iteration " + (i + 1) + ": Reward = " + reward);

            mcts.backpropagate(node, reward);

            if (reward > bestReward) {
                bestReward = reward;
                bestNode = node;
            }
        }

        if (bestNode != null) {
            log.debug("Total reward: " + bestNode.getTotalReward());

            var bestAction = bestNode.getAction();

            if (bestAction != null) {
                sendAction(bestAction, System.currentTimeMillis() + actionExecutionTime);
            }
        }
    }

//    @Override
//    protected void initializeThreadPool() {
//        pool = Executors.newFixedThreadPool(getMinimumNumberOfThreads());
//    }
//
//    @Override
//    protected int getMinimumNumberOfThreads() {
//        return super.getMinimumNumberOfThreads() + maximumUnitCount;
//    }

    @Override
    public void shutdown() {
//        pool.shutdownNow();
    }
}
