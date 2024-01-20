package at.ac.tuwien.ifs.sge.agent;


import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.ArrayList;
import java.util.Random;

public class EmpireMCTS {
    private static final Random random = new Random();
    private static final int maximumSimulationDepth = 20;
    private static final int simulationTime = 1000;

    private final Empire game;
    private final int playerId;
    private final EmpireMCTSNode root;

    public EmpireMCTS(Empire game, int playerId, EmpireMCTSNode root) {
        this.game = game;
        this.playerId = playerId;
        this.root = root;
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

    public EmpireMCTSNode expand(EmpireMCTSNode node) {
        return node.obtainUnvisitedChild(game, playerId);
    }

    public double simulate(EmpireMCTSNode node, long endTime) {
        int simulationDepth = 0;

        while (!game.isGameOver() && simulationDepth <= maximumSimulationDepth && System.currentTimeMillis() < endTime) {
            var possibleActions = game.getPossibleActions(playerId);
            var possibleMoveActions = new ArrayList<MovementStartOrder>();

            for (var action : possibleActions) {
                if (action instanceof MovementStartOrder movementStartOrder) {
                    possibleMoveActions.add(movementStartOrder);
                }
            }

            if (!possibleMoveActions.isEmpty()) {
                var nextAction = Util.selectRandom(possibleMoveActions, random);
                game.scheduleActionEvent(new GameActionEvent<>(playerId, nextAction, game.getGameClock().getGameTimeMs() + 1));
            }

            try {
                game.advance(simulationTime);
            } catch (ActionException e) {
                game.getLogger().logError(e.getMessage());
            }
        }

        if (game.isGameOver()) {
            return game.getGameUtilityValue()[playerId];
        } else {
            return game.getGameHeuristicValue()[playerId];
        }
    }

    public void backpropagate(EmpireMCTSNode node, double reward) {
        var currentNode = node;

        while (currentNode != null) {
            currentNode.update(reward);

            currentNode = currentNode.getParent();
        }
    }
}
