package at.ac.tuwien.ifs.sge.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import at.ac.tuwien.ifs.sge.agent.discoveredBoard.DiscoveredBoard;
import at.ac.tuwien.ifs.sge.agent.unitHeuristics.UnitHeuristics;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

public class GameNode {
    private final int executionTime;
    private final int responsiblePlayerId;
    private GameNode parent;
    protected final int playerId;
    protected final int radetzkyPlayerId;
    private final Empire immutableGameState;
    private final EmpireEvent responsibleAction;
    private final ArrayList<EventHeuristics> unexploredActions;
    private List<GameNode> children;
    private int wins;
    private int visits;
    private final Random random;
    private final float[] heuristic;
    private final UnitHeuristics[] immutableUnitHeuristics;
    private final DiscoveredBoard[] immutableDiscoveredBoard;

    public GameNode(Random random, UnitHeuristics[] unitHeuristics, int executionTime, int radetzkyPlayerId, int playerId, int responsiblePlayerId, Empire immutableGameState, EmpireEvent responsibleAction, DiscoveredBoard[] discoveredBoard) {
        this.random = random;
        this.heuristic = new float[immutableGameState.getNumberOfPlayers()];
        this.radetzkyPlayerId = radetzkyPlayerId;
        this.executionTime = executionTime;
        this.playerId = playerId;
        this.responsiblePlayerId = responsiblePlayerId;
        this.immutableGameState = immutableGameState;
        this.responsibleAction = responsibleAction;
        this.immutableUnitHeuristics = unitHeuristics;
        this.immutableDiscoveredBoard = discoveredBoard;
        Empire gameState;
        var copyOfUnitHeuristics = UnitHeuristics.copy(unitHeuristics);
        var copyOfBoard = DiscoveredBoard.copy(discoveredBoard);
        try {
            gameState = getGameState(copyOfUnitHeuristics, copyOfBoard);
        } catch (ActionException e) {
            e.printStackTrace();
            heuristic[responsiblePlayerId] = Float.NEGATIVE_INFINITY;
            unexploredActions = new ArrayList<>();
            return;
        }
        this.unexploredActions = EventHeuristics.fromGameState(gameState, playerId, copyOfBoard[playerId]);
        if (!unexploredActions.isEmpty()) {
            var best = unexploredActions.get(0);
            for (int i = 1; i < unexploredActions.size(); i++) {
                var current = unexploredActions.get(i);
                if (current.heuristic > best.heuristic) {
                    best = current;
                }
            }
            this.heuristic[playerId] = best.heuristic;
        }
        // todo propagate to parent nodes?
        this.heuristic[responsiblePlayerId] = EventHeuristics.calculateTotalHeuristic(gameState, responsibleAction, copyOfBoard[responsiblePlayerId]);
        if (parent != null) parent.setHeuristic(heuristic[responsiblePlayerId], responsiblePlayerId);
    }

    private void setHeuristic(float childHeuristic, int playerId) {
        if (childHeuristic <= heuristic[playerId]) return;
        heuristic[playerId] = childHeuristic;
        if (parent != null) parent.setHeuristic(childHeuristic, playerId);
    }

    public void addChild(GameNode gameNode) {
        if (children == null) children = new ArrayList<>();
        children.add(gameNode);
        gameNode.parent = this;
    }

    private Empire getGameState(UnitHeuristics[] copyOfImmutableUnit, DiscoveredBoard[] copyOfImmutableBoard) throws ActionException {
        if (parent == null) return (Empire) immutableGameState.copy();
        Empire currentState = parent.getGameState(copyOfImmutableUnit, copyOfImmutableBoard);

        if (responsibleAction != null) {
            currentState.scheduleActionEvent(new GameActionEvent<>(playerId, responsibleAction, currentState.getGameClock().getGameTimeMs() + 1));
            for (int i = 0; i < copyOfImmutableBoard.length; i++) {
                copyOfImmutableUnit[i].apply(currentState, responsibleAction);
                copyOfImmutableBoard[i].apply(currentState, responsibleAction);
            }
        }
        currentState.advance(executionTime);
        for (int i = 0; i < copyOfImmutableBoard.length; i++) {
            copyOfImmutableUnit[i].advance(currentState, executionTime);
            copyOfImmutableBoard[i].advance(executionTime, currentState, copyOfImmutableUnit[i]);
        }

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

    public float heuristic(float exploitationConstant) {
        var ucb1 = upperConfidenceBound(exploitationConstant);
        var maxHeuristic = heuristic[0];
        for (int i = 0; i < heuristic.length; i++) {
            if (maxHeuristic < heuristic[i]) maxHeuristic = heuristic[i];
        }
        return ucb1 + heuristic[responsiblePlayerId];// maxHeuristic;// heuristic[radetzkyPlayerId]; // todo really +? Would * be better?
    }

    private float upperConfidenceBound(float exploitationConstant) {
        float visits;
        if (this.visits == 0) visits = 1;
        else visits = this.visits;

        float N;
        if (parent != null) N = parent.getVisits();
        else N = visits;

        return (wins / visits) + exploitationConstant * (float) Math.sqrt(2 * Math.log(N) / visits);
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

    public GameNode getBestChildByHeuristics(float exploitationConstant) {
        if (isLeaf()) return this;
        var best = children.get(0);
        var bestHeuristic = best.heuristic(exploitationConstant);
        for (int i = 1; i < children.size(); i++) {
            var current = children.get(i);
            var currentHeuristic = current.heuristic(exploitationConstant);
            if (currentHeuristic > bestHeuristic) {
                best = current;
                bestHeuristic = currentHeuristic;
            }
        }
        return best;
    }

    public GameNode getBestByHeuristicRecursively(float exploitationConstant) {
        if (isLeaf()) return this;
        var bestChild = getBestChildByHeuristics(exploitationConstant);
        if (bestChild.hasUnexploredActions()) {
            // if it has a better heuristic value keep exploring the root node even though it is no leaf
            if (bestChild.heuristic(exploitationConstant) < this.heuristic(exploitationConstant)) return this;
        }
        return bestChild.getBestByHeuristicRecursively(exploitationConstant);
    }

    public void expand() {
        int nextPlayerId = getNextPlayerId();
        if (isRoot()) {
            for (int i = 0; i < unexploredActions.size(); i++) {
                var event = unexploredActions.get(i).event;
                addChild(new GameNode(random, immutableUnitHeuristics, executionTime, radetzkyPlayerId, nextPlayerId, playerId, immutableGameState, event, immutableDiscoveredBoard));
            }
            unexploredActions.clear();
            addChild(new GameNode(random, immutableUnitHeuristics, executionTime, radetzkyPlayerId, nextPlayerId, playerId, immutableGameState, null, immutableDiscoveredBoard));
        } else if (isLeaf()) {
            addChild(new GameNode(random, immutableUnitHeuristics, executionTime, radetzkyPlayerId, nextPlayerId, playerId, immutableGameState, null, immutableDiscoveredBoard));
        } else {
            var action = popUnexploredAction();
            addChild(new GameNode(random, immutableUnitHeuristics, executionTime, radetzkyPlayerId, nextPlayerId, playerId, immutableGameState, action, immutableDiscoveredBoard));
        }
    }

    public void simulate(Random random, int simulationDepth, long timeOfNextDecision, Logger log) {
        var depth = 0;
        var unitHeuristics = UnitHeuristics.copy(immutableUnitHeuristics); // todo make one for each player
        var discoveredBoards = DiscoveredBoard.copy(immutableDiscoveredBoard);
        var numberOfPlayers = immutableGameState.getNumberOfPlayers();
        Empire game;
        try {
            game = getGameState(unitHeuristics, discoveredBoards);
        } catch (ActionException e) {
            return;
        }
        var currentPlayer = playerId;
        try {
            while (!game.isGameOver() && depth++ <= simulationDepth && System.currentTimeMillis() < timeOfNextDecision) {
                var possibleActions = game.getPossibleActions(currentPlayer);
                if (possibleActions.size() > 0) {
                    var nextAction = EventHeuristics.selectBestRandomly(random, game, possibleActions, discoveredBoards[currentPlayer]);
                    if (nextAction != null) {
                        game.scheduleActionEvent(new GameActionEvent<>(currentPlayer, nextAction, game.getGameClock().getGameTimeMs() + 1));
                        for (int i = 0; i < numberOfPlayers; i++) {
                            unitHeuristics[i].apply(game, nextAction);
                            discoveredBoards[i].apply(game, nextAction);
                        }
                    }
                }
                game.advance(executionTime);
                for (int i = 0; i < numberOfPlayers; i++) {
                    unitHeuristics[i].advance(game, executionTime);
                    discoveredBoards[i].advance(executionTime, game, unitHeuristics[i]);
                }
                currentPlayer = (currentPlayer + 1) % numberOfPlayers;
            }
        } catch (ActionException e) {
            if (e.getMessage().contains("produce unit with id ")) return; // happens when a unit is produced, but the occupying unit leaves the city in the meantime
        }
        // todo maybe somehow add heuristic gained in the simulation to the heuristic value of the node, if the win counter does not reflect that gained knowledge
        //System.out.println("evaluate results");
        evaluateResults(game, discoveredBoards, unitHeuristics);
    }

    private void evaluateResults(Empire gameState, DiscoveredBoard[] discoveredBoard, UnitHeuristics[] unitHeuristics) {
        int bestPlayerId = 0;
        float myPerformance = Float.NEGATIVE_INFINITY;
        float bestPerformance = EventHeuristics.calculatePostSimulation(bestPlayerId, gameState, discoveredBoard[bestPlayerId], unitHeuristics[bestPlayerId]);
        if (bestPlayerId == playerId) {
            myPerformance = bestPerformance;
        }
        for (int i = 1; i < gameState.getNumberOfPlayers(); i++) {
            var current = EventHeuristics.calculatePostSimulation(i, gameState, discoveredBoard[i], unitHeuristics[i]);
            if (i == playerId) {
                myPerformance = current;
            }
            if (current > bestPerformance) {
                bestPlayerId = i;
            }
        }

        incrementVisits();
        if (bestPlayerId == radetzkyPlayerId || (myPerformance == bestPerformance && playerId == radetzkyPlayerId)) {
            incrementWins();
            if (parent != null) parent.backPropagate(radetzkyPlayerId); // best id and radetzky id might be different if best and radetzky are equally good
        } else if (parent != null) {
            parent.backPropagate(bestPlayerId);
        }
    }

    private void backPropagate(int winningPlayerId) {
        incrementVisits();
        if (winningPlayerId == radetzkyPlayerId) incrementWins();
        if (parent != null) parent.backPropagate(winningPlayerId);
    }

    public void print(Logger logger) {
        logger.info(toString());
        if (isLeaf()) return;
        for (int i = 0; i < children.size(); i++) {
            logger.info("child #" + i + ": " + children.get(i).toString());
        }
    }

    public String toString() {
        return responsibleAction + " visits: " + visits + " wins: " + wins + " all heuristic: " + Arrays.toString(heuristic) + " heuristic: " + heuristic(Radetzky.DEFAULT_EXPLOITATION_CONSTANT);
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
// todo add cache for gamestate in game node
