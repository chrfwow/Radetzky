package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.VisionUpdate;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.MovementAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class Radetzky extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    private final Random random = new Random();

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
        // Gets all the cities the player can see:
        var cities = getGame().getCitiesByPosition();

//        log.info("CITIES");
//
//        BiConsumer<Position, EmpireCity> printTest = (position, city) -> {
//            log.info("\tNAME: " + city.getName());
//            log.info("\tPOSITION: " + position.toString());
//            log.info("\tPLAYER ID: " + city.getPlayerId());
//            log.info("\tSTATE: " + city.getState());
//            log.info("\n");
//        };
//
//        cities.forEach(printTest);
//
//        log.info("DISCOVERED MAP:\n" + getGame().getBoard().getDiscoveredMap(playerId) + "\n");

        var unit = getGame().getUnitsByPlayer(playerId).get(0);

        moveUnit(unit);
    }

    @Override
    protected void onGameUpdate(HashMap<EmpireEvent, ActionResult> actionsWithResult) {
        BiConsumer<EmpireEvent, ActionResult> handleAction = (action, result) -> {
            if (action instanceof VisionUpdate visionUpdate) {
//                log.info("NEW TERRAIN");
//
//                BiConsumer<Position, EmpireTerrain> printTest = (position, terrain) -> {
//                    log.info("\tNAME: " + terrain.getName());
//                    log.info("\tPOSITION: " + position.toString());
//                    log.info("\tMAP ID: " + terrain.getMapIdentifier());
//                    log.info("\tTO STRING: " + terrain);
//                    log.info("\n");
//                };
//
//                visionUpdate.getNewActive().forEach(printTest);
            } else if (action instanceof MovementAction movementAction) {
                moveUnit(movementAction.getUnitId());
            } else {
                log.info("ACTION: " + action);
            }
        };

        actionsWithResult.forEach(handleAction);
    }

    @Override
    protected void onActionRejected(EmpireEvent action) {
        if (action instanceof MovementAction movementAction) {
            moveUnit(movementAction.getUnitId());
        }
    }

    @Override
    protected void initializeThreadPool() {
        pool = Executors.newFixedThreadPool(getMinimumNumberOfThreads());
    }

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 2;
    }

    @Override
    public void shutdown() {

    }

    private void moveUnit(EmpireUnit unit) {
        var nextPosition = getNextPosition(unit.getPosition());
        var movementStartOrder = new MovementStartOrder(unit, nextPosition);

        sendAction(movementStartOrder, System.currentTimeMillis() + 100);
    }

    private void moveUnit(UUID unitId) {
        moveUnit(getGame().getUnit(unitId));
    }

    private Position getNextPosition(Position currentPosition) {
        var x = currentPosition.getX();
        var y = currentPosition.getY();
        var board = getGame().getBoard();
        var mapSize = board.getMapSize();
        EmpireTerrain tile = null;

        while ((x == currentPosition.getX() && y == currentPosition.getY()) ||
                tile.getMapIdentifier() == 'm' ||
                tile.isOccupied()) {

            x = currentPosition.getX() + random.nextInt(3) - 1;
            y = currentPosition.getY() + random.nextInt(3) - 1;

            if (x < 0) {
                x = 0;
            }

            if (x >= mapSize.getWidth()) {
                x = mapSize.getWidth() - 1;
            }

            if (y < 0) {
                y = 0;
            }

            if (y >= mapSize.getHeight()) {
                y = mapSize.getHeight() - 1;
            }

            try {
                tile = board.getTile(x, y);
            } catch (EmpireMapException e) {
                throw new RuntimeException(e);
            }
        }

        return new Position(x, y);
    }
}
