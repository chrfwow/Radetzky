package at.ac.tuwien.ifs.sge.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

public class GameNode {
    private final int executionTime;
    private GameNode parent;
    protected final int playerId;
    private final Empire immutableGameState;
    private final EmpireEvent responsibleAction;
    private final ArrayList<EventHeuristics> unexploredActions;
    private List<GameNode> children;
    private int wins;
    private int visits;
    private final double heuristic;
    private final UnitHeuristics immutableUnitHeuristics;

    public GameNode(UnitHeuristics unitHeuristics, int executionTime, int playerId, Empire immutableGameState, EmpireEvent responsibleAction, double discoveredTilesRatio) {
        this.executionTime = executionTime;
        this.playerId = playerId;
        this.immutableGameState = immutableGameState;
        this.responsibleAction = responsibleAction;
        this.immutableUnitHeuristics = unitHeuristics;
        Empire gameState;
        var copyOfUnitHeuristics = unitHeuristics.copy();
        try {
            gameState = getGameState(copyOfUnitHeuristics);
        } catch (ActionException e) {
            e.printStackTrace();
            heuristic = Double.NEGATIVE_INFINITY;
            unexploredActions = new ArrayList<>();
            return;
        }
        this.unexploredActions = EventHeuristics.fromGameState(copyOfUnitHeuristics, gameState, playerId, discoveredTilesRatio);
        // todo propagate to parent nodes?
        this.heuristic = EventHeuristics.calculateTotalHeuristic(copyOfUnitHeuristics, gameState, responsibleAction, discoveredTilesRatio);
    }

    public void addChild(GameNode gameNode) {
        if (children == null) children = new ArrayList<>();
        children.add(gameNode);
        gameNode.parent = this;
    }

    private Empire getGameState(UnitHeuristics copyOfImmutable) throws ActionException {
        if (parent == null) return (Empire) immutableGameState.copy();
        Empire currentState = parent.getGameState(copyOfImmutable);

        if (responsibleAction != null) {
            currentState.scheduleActionEvent(new GameActionEvent<>(playerId, responsibleAction, currentState.getGameClock().getGameTimeMs() + 1));
            copyOfImmutable.apply(currentState, responsibleAction);
        }
        currentState.advance(executionTime);
        copyOfImmutable.advance(currentState);
        return currentState;
    }

    public int getNrOfUnexploredActions() {
        return unexploredActions.size();
    }

    public boolean hasUnexploredActions() {
        return !unexploredActions.isEmpty();
    }

    public EmpireEvent popUnexploredAction() {
        if (unexploredActions.isEmpty()) return null;

        var bestIndex = 0;
        var best = unexploredActions.get(0);
        var bestHeuristic = best.heuristic;
        for (int i = 1; i < unexploredActions.size(); i++) {
            if (unexploredActions.get(i).heuristic > bestHeuristic) {
                best = unexploredActions.get(i);
                bestIndex = i;
                bestHeuristic = best.heuristic;
            }
        }
        unexploredActions.remove(bestIndex);
        return best.event;
    }

    public int getNextPlayerId() {
        return (playerId + 1) % immutableGameState.getNumberOfPlayers();
    }

    public int getPlayerId() {
        return playerId;
    }

    public EmpireEvent getResponsibleAction() {
        return responsibleAction;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getVisits() {
        return visits;
    }

    public void setVisits(int visits) {
        this.visits = visits;
    }

    public void incrementWins() {
        wins++;
    }

    public void incrementVisits() {
        visits++;
    }

    public double heuristic(double exploitationConstant) {
        var ucb1 = upperConfidenceBound(exploitationConstant);
        return ucb1 + heuristic;
    }

    private double upperConfidenceBound(double exploitationConstant) {
        double visits;
        if (this.visits == 0) visits = 1;
        else visits = this.visits;

        double N;
        if (parent != null) N = parent.getVisits();
        else N = visits;

        return (wins / visits) + exploitationConstant * Math.sqrt(Math.log(N) / visits);
    }

    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    public boolean isRoot() {
        return parent == null;
    }

    public GameNode getBestChild(Comparator<GameNode> comparator) {
        if (isLeaf()) return this;
        return Collections.max(children, comparator);
    }

    public void expand(double discoveredTilesRatio) {
        int nextPlayerId = getNextPlayerId();
        if (isRoot()) {
            while (hasUnexploredActions()) {
                var action = popUnexploredAction();
                addChild(new GameNode(immutableUnitHeuristics, executionTime, nextPlayerId, immutableGameState, action, discoveredTilesRatio));
            }
            addChild(new GameNode(immutableUnitHeuristics, executionTime, nextPlayerId, immutableGameState, null, discoveredTilesRatio));
        } else if (isLeaf()) {
            addChild(new GameNode(immutableUnitHeuristics, executionTime, nextPlayerId, immutableGameState, null, discoveredTilesRatio));
        } else {
            var action = popUnexploredAction();
            addChild(new GameNode(immutableUnitHeuristics, executionTime, nextPlayerId, immutableGameState, action, discoveredTilesRatio));
        }
    }

    public GameNode selection(Comparator<GameNode> comparator) {
        if (isLeaf()) return this;
        var bestChild = getBestChild(comparator);
        if (bestChild.hasUnexploredActions()) {
            // if it has a better heuristic value keep exploring the root node even though it is no leaf
            if (comparator.compare(this, bestChild) >= 0) {
                return this;
            }
        }
        return bestChild.selection(comparator);
    }

    public boolean[] simulate(Random random, int simulationDepth, long timeOfNextDecision, Logger log, double discoveredTilesRatio) {
        var depth = 0;
        var unitHeuristics = immutableUnitHeuristics.copy();
        Empire game;
        try {
            game = getGameState(unitHeuristics);
        } catch (ActionException e) {
            return null;
        }
        var currentPlayer = playerId;
        try {
            while (!game.isGameOver() && depth++ <= simulationDepth && System.currentTimeMillis() < timeOfNextDecision) {
                var possibleActions = game.getPossibleActions(currentPlayer);
                if (possibleActions.size() > 0) {
                    var doNothing = random.nextInt(possibleActions.size()) == 0; // todo use heuristic
                    if (!doNothing) {
                        var nextAction = EventHeuristics.selectBest(unitHeuristics, game, possibleActions, discoveredTilesRatio);
                        game.scheduleActionEvent(new GameActionEvent<>(currentPlayer, nextAction, game.getGameClock().getGameTimeMs() + 1));
                        unitHeuristics.apply(game, nextAction);
                    }
                }
                game.advance(executionTime);
                unitHeuristics.advance(game);
                currentPlayer = (currentPlayer + 1) % game.getNumberOfPlayers();
            }
        } catch (ActionException e) {
            if (e.getMessage().contains("produce unit with id ")) return null; // happens when a unit is produced, but the occupying unit leaves the city in the meantime
        }
        return determineWinner(game);
    }

    private boolean[] determineWinner(Empire game) {
        var winners = new boolean[game.getNumberOfPlayers()];
        if (game.isGameOver()) {
            var evaluation = game.getGameUtilityValue();
            for (var pid = 0; pid < game.getNumberOfPlayers(); pid++) {
                if (evaluation[pid] == 1D)
                    winners[pid] = true;
            }
        } else {
            var evaluation = game.getGameHeuristicValue();
            var maxIndex = 0;
            for (var pid = 1; pid < game.getNumberOfPlayers(); pid++) {
                if (evaluation[pid] > evaluation[maxIndex])
                    maxIndex = pid;
            }
            winners[maxIndex] = true;
        }
        return winners;
    }

    public void backPropagation(boolean[] winners) {
        if (winners == null) return;
        incrementVisits();
        var playerId = (this.playerId - 1);
        if (playerId < 0) playerId = immutableGameState.getNumberOfPlayers() - 1;
        if (winners[playerId]) incrementWins();
        if (parent != null) parent.backPropagation(winners);
    }

    public void print(Logger logger) {
        logger.info(toString());
        if (isLeaf()) return;
        for (int i = 0; i < children.size(); i++) {
            logger.info("child #" + i + ": " + children.get(i).toString());
        }
    }

    public String toString() {
        return responsibleAction + " visits: " + visits + " wins: " + wins + " heuristic: " + heuristic(Radetzky.DEFAULT_EXPLOITATION_CONSTANT);
    }

    public GameNode getMostVisitedChild() {
        if (isLeaf()) return this;
        GameNode mostVisited = children.get(0);
        int visits = mostVisited.visits;
        for (int i = 1; i < children.size(); i++) {
            var current = children.get(i);
            var currentVisits = current.visits;
            if (currentVisits > visits) {
                visits = currentVisits;
                mostVisited = current;
            }
        }
        return mostVisited;
    }
}