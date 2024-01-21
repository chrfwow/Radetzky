package at.ac.tuwien.ifs.sge.agent;


import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class EmpireMCTS {
    private static final Random random = new Random();
    private static final int maximumSimulationDepth = 20;
    private static final int simulationTime = 1000;

    private final EmpireMCTSNode root;

    public EmpireMCTS(Empire game, EmpireUnit unit) {
        this.root = new EmpireMCTSNode(game, unit, null, null);
    }

    public EmpireMCTSNode select() {
        var node = root;

        while (!node.terminal()) {
            var bestNode = node.obtainBestChild();

            if (bestNode == null) {
                break;
            }

            node = bestNode;
        }

        return node;
    }

    public EmpireMCTSNode expand(Empire game, EmpireUnit unit, EmpireMCTSNode node) {
        return node.obtainUnvisitedChild(game, unit);
    }

    public int simulate(Empire game, EmpireUnit unit) {
        int simulationDepth = 0;

        long currentSimulationTime = System.currentTimeMillis() + simulationTime;

        while (!game.isGameOver() && simulationDepth <= maximumSimulationDepth && System.currentTimeMillis() < currentSimulationTime) {
            Set<EmpireEvent> possibleActions = null;
            try {
                possibleActions = game.getBoard().getPossibleActions(unit);
                var possibleMoveActions = new ArrayList<MovementStartOrder>();

                for (var action : possibleActions) {
                    if (action instanceof MovementStartOrder movementStartOrder) {
                        possibleMoveActions.add(movementStartOrder);
                    }
                }

                if (!possibleMoveActions.isEmpty()) {
                    var nextAction = Util.selectRandom(possibleMoveActions, random);
                    game.scheduleActionEvent(new GameActionEvent<>(unit.getPlayerId(), nextAction, game.getGameClock().getGameTimeMs() + 1));
                }
            } catch (EmpireMapException e) {
                game.getLogger().logError(e.getMessage());
            }

            try {
                game.advance(simulationTime);
            } catch (ActionException e) {
                game.getLogger().logError(e.getMessage());
            }

            ++simulationDepth;
        }

        var playerIndex = unit.getPlayerId();
        var enemyPlayerIndex = (playerIndex + 1) % 2;

        if (game.isGameOver()) {
            var utilityValues = game.getGameUtilityValue();

            if (utilityValues[playerIndex] > utilityValues[enemyPlayerIndex]) {
                return 1000; // Win
            } else if (utilityValues[playerIndex] < utilityValues[enemyPlayerIndex]) {
                return -1000; // Loss
            } else {
                return 100; // Draw
            }
        } else {
            var heuristicValues = game.getGameHeuristicValue();

//            game.getLogger().LOGGER().debug("Player = " + heuristicValues[playerIndex] + " VS Enemy = " + heuristicValues[enemyPlayerIndex]);

            if (heuristicValues[playerIndex] > heuristicValues[enemyPlayerIndex]) {
                return 50; // Better
            } else if (heuristicValues[playerIndex] < heuristicValues[enemyPlayerIndex]) {
                return -50; // Worse
            } else {
                return 5; // Same
            }
        }
    }

    public void backpropagate(EmpireMCTSNode node, int reward) {
        var currentNode = node;

        while (currentNode != null) {
            currentNode.update(reward);

            currentNode = currentNode.getParent();
        }
    }
}
