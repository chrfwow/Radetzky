package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.HashMap;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    private static final int START_DELAY_IN_MILLISECONDS = 1000;
    private static final int TIMER_INTERVAL_IN_MILLISECONDS = 100;
    private static final int ACTION_EXECUTION_TIME_IN_MILLISECONDS = 100;

    private final Radetzky radetzky = this;
    private final Random random = new Random();
    private final Timer timer = new Timer();

    private boolean actionInProgress = false;

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
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!actionInProgress) {
                    var actions = getGame().getPossibleActions(playerId);

                    log.debug("Possible actions: " + actions.size() + ":");

                    for (var action : actions) {
                        log.debug("\t" + action.toString());
                    }

                    var nextAction = Util.selectRandom(actions, random);

                    radetzky.sendAction(nextAction, System.currentTimeMillis() + ACTION_EXECUTION_TIME_IN_MILLISECONDS);

                    log.debug("Next Action:\n\t" + nextAction.toString());

                    actionInProgress = true;
                }
            }
        };

        timer.schedule(task, START_DELAY_IN_MILLISECONDS, TIMER_INTERVAL_IN_MILLISECONDS);
    }

    @Override
    protected void onGameUpdate(HashMap<EmpireEvent, ActionResult> actionsWithResult) {
        log.debug("onGameUpdate");
        actionInProgress = false;
    }

    @Override
    protected void onActionRejected(EmpireEvent action) {
        log.debug("onActionRejected");
        actionInProgress = false;
    }

    @Override
    public void shutdown() {
        timer.cancel();
    }
}
