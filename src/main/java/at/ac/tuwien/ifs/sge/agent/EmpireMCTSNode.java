package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EmpireMCTSNode {
    private static final Random random = new Random();
    private static final int actionExecutionTime = 10;

    private final EmpireMCTSNode parent;
    private final EmpireEvent action;

    private int visitCount = 0;
    private double totalReward = 0;

    private List<EmpireMCTSNode> children = new ArrayList<>();
    private List<EmpireEvent> unexploredActions = new ArrayList<>();

    public EmpireMCTSNode(Empire game, int playerId, EmpireMCTSNode parent, EmpireEvent action) {
        this.parent = parent;
        this.action = action;

        try {
            var gameCopy = game.copy();

            gameCopy.applyAction(action, System.currentTimeMillis() + actionExecutionTime);
            gameCopy.advance(actionExecutionTime);

            unexploredActions = game.getPossibleActions(playerId).stream().toList();
        } catch (ActionException e) {
            game.getLogger().logError(e.getMessage());
        }
    }

    public void update(double reward) {
        totalReward += reward;
        ++visitCount;
    }

    public EmpireMCTSNode obtainBestChild() {
        if (children.isEmpty()) {
            return null;
        }

        double bestAverageReward = 0.0;
        EmpireMCTSNode bestChild = null;

        for (var child : children) {
            double averageReward = 0.0;

            if (child.visitCount > 0) {
                averageReward = child.totalReward / child.visitCount;
            }

            if (averageReward > bestAverageReward) {
                bestAverageReward = averageReward;
                bestChild = child;
            }
        }

        return bestChild;
    }

    public EmpireMCTSNode obtainRandomChild() {
        if (children.isEmpty()) {
            return null;
        }

        return Util.selectRandom(children, random);
    }

    public EmpireMCTSNode obtainUnvisitedChild(Empire game, int playerId) {
        if (unexploredActions.isEmpty()) {
            return null;
        }

        int index = random.nextInt(unexploredActions.size());

        var unexploredAction = unexploredActions.remove(index);

        return new EmpireMCTSNode(game, playerId, this, unexploredAction);
    }

    private int obtainReward() {
        return 0;
    }

    public boolean terminal() {
        return children.isEmpty() || fullyExpanded();
    }

    public boolean fullyExpanded() {
        return unexploredActions.isEmpty();
    }

    public EmpireMCTSNode getParent() {
        return parent;
    }
}
