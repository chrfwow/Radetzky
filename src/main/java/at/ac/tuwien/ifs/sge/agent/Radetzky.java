package at.ac.tuwien.ifs.sge.agent;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    public static final double DEFAULT_EXPLOITATION_CONSTANT = Math.sqrt(2);
    private Future<?> mctsIterationFuture;
    private volatile boolean isRunning;

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
        isRunning = true;
        mctsIterationFuture = pool.submit(this::playSimulation);
    }

    @Override
    protected void onGameUpdate(EmpireEvent action, ActionResult result) {
    }

    @Override
    protected void onActionRejected(EmpireEvent action) {
        log.error("Rejected " + action.getClass().getSimpleName() + " action " + action);
    }

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 2;
    }

    @Override
    protected void initializeThreadPool() {
        log.info("using " + getMinimumNumberOfThreads() + " threads");
        pool = Executors.newFixedThreadPool(getMinimumNumberOfThreads());
    }

    @Override
    public void shutdown() {
        isRunning = false;
        mctsIterationFuture.cancel(true);
    }

    private void playSimulation() {
        log.info("play simulation");
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
        }
        log.info("send prod order");
        var copy = (Empire) game.copy();

        var actions = copy.getPossibleActions(playerId);
        for (EmpireEvent action : actions) {
            if (action instanceof ProductionStartOrder) {
                sendAction(action, System.currentTimeMillis() + 50);
                break;
            }
        }
        while (isRunning) {
            copy = (Empire) game.copy();
            try {
                log.info("advancing...");
                copy.advance(1000);
            } catch (ActionException e) {
                log.info(e.getMessage());
                var cause = e.getCause();
                if (cause != null) log.info(cause.getMessage());
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }

        log.info("stopped playing");
    }
}
