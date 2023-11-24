package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Size;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;

public class DiscoveredBoard {
    private final int tilesCount;
    private final float ratioPerTile;
    private float discoveredBoardRatio;

    private DiscoveredBoard(int tilesCount) {
        this.tilesCount = tilesCount;
        this.ratioPerTile = 1f / tilesCount;
    }

    public DiscoveredBoard(int tilesCount, float ratioPerTile, float discoveredBoardRatio) {
        this.tilesCount = tilesCount;
        this.ratioPerTile = ratioPerTile;
        this.discoveredBoardRatio = discoveredBoardRatio;
    }


    public DiscoveredBoard copy() {
        return new DiscoveredBoard(tilesCount, ratioPerTile, discoveredBoardRatio);
    }

    public float getDiscoveredBoardRatio() {
        return discoveredBoardRatio;
    }

    public static DiscoveredBoard get(Empire gameState) {
        var map = gameState.getBoard();
        var size = map.getMapSize();
        var discoveredBoard = new DiscoveredBoard(size.getHeight() * size.getWidth());
        discoveredBoard.discoveredBoardRatio = getDiscoveredTilesRatio(map, size);
        return discoveredBoard;
    }

    private static float getDiscoveredTilesRatio(EmpireMap board, Size size) {
        int discovered = 0;
        int undiscovered = 0;
        try {
            for (int x = 0; x < size.getWidth(); x += 3) {
                for (int y = 0; y < size.getHeight(); y += 3) {
                    if (board.getTile(x, y) == null) undiscovered++;
                    else discovered++;
                }
            }
        } catch (EmpireMapException e) {
            e.printStackTrace();
        }
        return ((float) discovered) / (undiscovered + discovered);
    }

    public void apply(Empire gameState, EmpireEvent nextAction) {
        if (nextAction instanceof MovementStartOrder movementStartOrder) {
            var newlyDiscoveredTiles = getNumberOfNewUndiscoveredTiles(gameState, movementStartOrder);
            discoveredBoardRatio += ratioPerTile * newlyDiscoveredTiles;
        }
    }

    public static float calculateHeuristics(Empire gameState, EmpireEvent empireEvent, DiscoveredBoard discoveredBoard) {
        if (empireEvent instanceof MovementStartOrder movementStartOrder) {
            var newlyDiscoveredTiles = getNumberOfNewUndiscoveredTiles(gameState, movementStartOrder);
            var unitType = gameState.getUnit(movementStartOrder.getUnitId()).getUnitTypeId();
            return ((newlyDiscoveredTiles * UnitStats.speedOfType[unitType]) / 9f) *
                    (1f - discoveredBoard.discoveredBoardRatio); // the more tiles have been uncovered, the less sense it makes to explore
            // todo can scouts discover more than 9 tiles per move?
        }
        return 0;
    }

    private static int getNumberOfNewUndiscoveredTiles(Empire gameState, MovementStartOrder movementStartOrder) {
        var unit = gameState.getUnit(movementStartOrder.getUnitId());
        if (unit == null) {
            System.err.println("Unit with id " + movementStartOrder.getUnitId() + " is null");
            return 0;
        }
        var fov = unit.getFov();
        var map = gameState.getBoard();
        var destination = movementStartOrder.getDestination();
        var newlyDiscoveredTiles = 0;
        try {
            for (int x = -fov; x <= fov; x++) {
                for (int y = -fov; y <= fov; y++) {
                    if (x == 0 && y == 0) continue;
                    var actualX = destination.getX() + x;
                    var actualY = destination.getY() + y;
                    if (!map.isInside(actualX, actualY)) continue;
                    EmpireTerrain tile = map.getTile(actualX, actualY);
                    if (tile == null) newlyDiscoveredTiles++;
                }
            }
        } catch (EmpireMapException e) {
            e.printStackTrace();
        }
        return newlyDiscoveredTiles;
    }
}
