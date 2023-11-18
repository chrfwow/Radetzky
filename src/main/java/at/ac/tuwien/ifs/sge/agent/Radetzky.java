package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.ProductionAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.concurrent.Executors;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
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
        var city = game.getUnitsByPlayer(playerId).get(0);
        var unitTypeId = 2; // Scout
        var productionStartOrder = new ProductionStartOrder(city.getPosition(), unitTypeId);

        sendAction(productionStartOrder, System.currentTimeMillis() + 50);
    }

    @Override
    protected void onGameUpdate(EmpireEvent action, ActionResult result) {
        if (action instanceof ProductionAction productionAction) {
            var city = game.getCity(productionAction.getCityPosition());

            if (city.getPlayerId() == playerId) {
                var newUnit = game.getUnit(productionAction.getUnitId());

                var productionStartOrder = new ProductionStartOrder(city.getPosition(), newUnit.getUnitTypeId());

                sendAction(productionStartOrder, System.currentTimeMillis() + 50);
            }
        }
    }

    @Override
    protected void onActionRejected(EmpireEvent action) {

    }

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 2;
    }

    @Override
    protected void initializeThreadPool() {
        pool = Executors.newFixedThreadPool(getMinimumNumberOfThreads());
    }

    @Override
    public void shutdown() {

    }
}
