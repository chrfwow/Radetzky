package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EmpireMCTSNode {
    private static final Random random = new Random();
    private static final int actionExecutionTime = 10;

    private final EmpireMCTSNode parent;
    private final EmpireEvent action;

    private int visitCount = 0;
    private int totalReward = 0;

    private List<EmpireMCTSNode> children = new ArrayList<>();
    private List<EmpireEvent> unexploredActions = new ArrayList<>();

    public EmpireMCTSNode(Empire game, EmpireUnit unit, EmpireMCTSNode parent, EmpireEvent action) {
        this.parent = parent;
        this.action = action;

//        try {
//            var gameCopy = game.copy();
//
//            gameCopy.applyAction(action, System.currentTimeMillis() + actionExecutionTime);
//            gameCopy.advance(actionExecutionTime);

        try {
            var possibleActions = game.getBoard().getPossibleActions(unit);

            unexploredActions.addAll(possibleActions);
        } catch (EmpireMapException e) {
            game.getLogger().logError(e.getMessage());
        }
//        } catch (ActionException e) {
//            game.getLogger().logError(e.getMessage());
//        }
    }

    public void update(int reward) {
        totalReward += reward;
        ++visitCount;
    }

    public EmpireMCTSNode obtainBestChild() {
        if (children.isEmpty()) {
            return null;
        }

        float bestAverageReward = Integer.MIN_VALUE;
        EmpireMCTSNode bestChild = null;

        for (var child : children) {
            float averageReward = Integer.MIN_VALUE + 1;

            if (child.visitCount > 0) {
                averageReward = child.totalReward / (float) child.visitCount;
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

    public EmpireMCTSNode obtainUnvisitedChild(Empire game, EmpireUnit unit) {
        if (unexploredActions.isEmpty()) {
            return null;
        }

        int index = random.nextInt(unexploredActions.size());
        var unexploredAction = unexploredActions.remove(index);
        var newNode = new EmpireMCTSNode(game, unit, this, unexploredAction);

        children.add(newNode);

        return newNode;
    }

    public boolean terminal() {
        return children.isEmpty() && fullyExpanded();
    }

    public boolean fullyExpanded() {
        return unexploredActions.isEmpty();
    }

    public EmpireMCTSNode getParent() {
        return parent;
    }

    public EmpireEvent getAction() {
        return action;
    }

    public double getTotalReward() {
        return totalReward;
    }
}
