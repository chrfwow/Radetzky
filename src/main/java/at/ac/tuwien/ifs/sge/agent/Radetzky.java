package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.MovementAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    private static final int actionExecutionTime = 100;

    private final Random random = new Random();

    private Map<UUID, EmpireUnitState> unitStates = new HashMap<>();

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
        var units = getGame().getUnitsByPlayer(playerId);

        for (var unit : units) {
            unitStates.put(unit.getId(), EmpireUnitState.Idle);

            employUnit(unit);
        }
    }

    @Override
    protected void onGameUpdate(HashMap<EmpireEvent, ActionResult> actionsWithResult) {
        for (var entry : actionsWithResult.entrySet()) {
            handleOnGameUpdateAction(entry.getKey(), entry.getValue());
        }
    }

    private void handleOnGameUpdateAction(EmpireEvent action, ActionResult result) {
        log.debug("Action " + action.toString() + " ACCEPTED.");

        if (action instanceof MovementAction movementAction) {
            var unitId = movementAction.getUnitId();

            unitStates.put(unitId, EmpireUnitState.Idle);

            employUnit(getGame().getUnit(unitId));
        }
    }

    @Override
    protected void onActionRejected(EmpireEvent action) {
        log.debug("Action " + action.toString() + " REJECTED.");

        if (action instanceof MovementStartOrder movementStartOrder) {
            var unitId = movementStartOrder.getUnitId();

            unitStates.put(unitId, EmpireUnitState.Idle);

            employUnit(getGame().getUnit(unitId));
        }
    }

    private void employUnit(EmpireUnit unit) {
        if (unit == null) {
            return;
        }

        if (unitStates.getOrDefault(unit.getId(), EmpireUnitState.Idle) != EmpireUnitState.Idle) {
            return;
        }

        try {
            var possibleUnitActions = getGame().getBoard().getPossibleActions(unit);
            var possibleUnitMoveActions = new ArrayList<MovementStartOrder>();

            for (var action : possibleUnitActions) {
                if (action instanceof MovementStartOrder movementStartOrder) {
                    possibleUnitMoveActions.add(movementStartOrder);
                }
            }

            var nextAction = Util.selectRandom(possibleUnitMoveActions, random);

            sendAction(nextAction, System.currentTimeMillis() + actionExecutionTime);
        } catch (EmpireMapException e) {
            log.error("Actions for unit " + unit.getId() + " could not be retrieved.");
            log.printStackTrace(e);
        }
    }

    @Override
    public void shutdown() {
    }
}
