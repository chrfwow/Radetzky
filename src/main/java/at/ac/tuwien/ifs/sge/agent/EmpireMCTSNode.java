package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.EmpireAction;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EmpireMCTSNode {
    private static final Random random = new Random();

    private EmpireMCTSNode parent;
    private EmpireAction action;

    private int visitCount = 0;
    private int totalReward = 0;

    private List<EmpireMCTSNode> children = new ArrayList<>();

    public EmpireMCTSNode(EmpireMCTSNode parent, EmpireAction action) {
        this.parent = parent;
        this.action = action;
    }

    public void update(int reward) {
        totalReward += reward;
        ++visitCount;
    }

    private EmpireMCTSNode obtainBestChild() {
        if (children.isEmpty()) {
            return null;
        }

        float bestAverageReward = 0;
        EmpireMCTSNode bestChild = null;

        for (var child : children) {
            float averageReward = 0.0f;

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

    private EmpireMCTSNode obtainRandomChild() {
        if (children.isEmpty()) {
            return null;
        }

        return Util.selectRandom(children, random);
    }

    private int obtainReward() {
        return 0;
    }
}
