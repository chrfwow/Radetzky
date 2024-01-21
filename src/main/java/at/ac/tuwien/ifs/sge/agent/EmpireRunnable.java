//package at.ac.tuwien.ifs.sge.agent;
//
//import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
//import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
//import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;
//import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitState;
//
//public class EmpireRunnable implements Runnable {
//    private static final int decisionTime = 1000;
//
//    private final Radetzky radetzky;
//    private final Empire game;
//    private final EmpireUnit unit;
//
//    private EmpireUnitState unitState = EmpireUnitState.Idle;
//
//    private double bestReward = 0.0;
//    private EmpireMCTSNode bestNode = null;
//
//    public EmpireRunnable(Radetzky radetzky, Empire game, EmpireUnit unit) {
//        this.radetzky = radetzky;
//        this.game = game;
//        this.unit = unit;
//    }
//
//    @Override
//    public void run() {
//        EmpireMCTS mcts = null;
//        Empire gameCopy = null;
//
//        while (true) {
//            if (unitState == EmpireUnitState.Idle) {
//                bestReward = 0.0;
//
//                gameCopy = (Empire) game.copy();
//                gameCopy.stop();
//
//                mcts = new EmpireMCTS(gameCopy, unit);
//            }
//
//            var node = mcts.select();
//
//            if (node == null) {
//                continue;
//            }
//
//            node = mcts.expand(gameCopy, unit, node);
//
//            if (node == null) {
//                continue;
//            }
//
//            var reward = mcts.simulate(gameCopy, unit);
//            mcts.backpropagate(node, reward);
//
//            if (reward > bestReward) {
//                bestReward = reward;
//                bestNode = node;
//            }
//        }
//    }
//
//    public EmpireEvent getBestNextAction() {
//        var bestNextAction = bestNode.getAction();
//
//        bestNode = null;
//
//        return bestNextAction;
//    }
//
//    public void setUnitState(EmpireUnitState unitState) {
//        this.unitState = unitState;
//    }
//}
