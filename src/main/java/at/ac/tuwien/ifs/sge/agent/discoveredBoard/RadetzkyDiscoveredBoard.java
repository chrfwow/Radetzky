package at.ac.tuwien.ifs.sge.agent.discoveredBoard;

import at.ac.tuwien.ifs.sge.agent.UnitStats;
import at.ac.tuwien.ifs.sge.agent.unitHeuristics.UnitHeuristics;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.VisionUpdate;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Size;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

public class RadetzkyDiscoveredBoard implements DiscoveredBoard {
    private final int radetzkyPlayerId;
    private final int tilesCount;
    private final float ratioPerTile;
    private float discoveredBoardRatio;

    private RadetzkyDiscoveredBoard(int radetzkyPlayerId, int tilesCount) {
        this.radetzkyPlayerId = radetzkyPlayerId;
        this.tilesCount = tilesCount;
        this.ratioPerTile = 1f / tilesCount;
    }

    private RadetzkyDiscoveredBoard(int radetzkyPlayerId, int tilesCount, float ratioPerTile, float discoveredBoardRatio) {
        this.radetzkyPlayerId = radetzkyPlayerId;
        this.tilesCount = tilesCount;
        this.ratioPerTile = ratioPerTile;
        this.discoveredBoardRatio = discoveredBoardRatio;
    }

    @Override
    public RadetzkyDiscoveredBoard copy() {
        return new RadetzkyDiscoveredBoard(radetzkyPlayerId, tilesCount, ratioPerTile, discoveredBoardRatio);
    }

    @Override
    public float getDiscoveredBoardRatio() {
        return discoveredBoardRatio;
    }

    public static RadetzkyDiscoveredBoard get(int radetzkyPlayerId, Empire gameState) {
        var map = gameState.getBoard();
        var size = map.getMapSize();
        var discoveredBoard = new RadetzkyDiscoveredBoard(radetzkyPlayerId, size.getHeight() * size.getWidth());
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

    @Override
    public void apply(Empire gameState, EmpireEvent nextAction) {
        if (nextAction instanceof VisionUpdate visionUpdate) {
            if (visionUpdate.getPlayerId() != radetzkyPlayerId) return;
            var map = gameState.getBoard();
            var size = map.getMapSize();
            discoveredBoardRatio = getDiscoveredTilesRatio(map, size);
        } else if (nextAction instanceof MovementStartOrder movementStartOrder) {
            var unit = gameState.getUnit(movementStartOrder.getUnitId());
            if (unit.getPlayerId() != radetzkyPlayerId) return;
            var newlyDiscoveredTiles = getNumberOfNewUndiscoveredTiles(gameState, movementStartOrder, unit);
            discoveredBoardRatio += ratioPerTile * newlyDiscoveredTiles;
        }
    }

    @Override
    public void advance(long millis, Empire gameState, UnitHeuristics unitHeuristics) {
    }

    @Override
    public int getNumberOfNewUndiscoveredTiles(Empire gameState, MovementStartOrder movementStartOrder, EmpireUnit unit) {
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

    @Override
    public int getPlayerId() {
        return radetzkyPlayerId;
    }
}
