package at.ac.tuwien.ifs.sge.agent;

import java.util.ArrayList;
import java.util.Set;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;

public class EventHeuristics {
    public final EmpireEvent event;
    public final double heuristic;

    // todo use playerId, if other player than -heuristic
    public EventHeuristics(Empire gameState, EmpireEvent event, double discoveredTilesRatio) {
        this.event = event;
        this.heuristic = calculateTotalHeuristic(gameState, event, discoveredTilesRatio);
    }

    public static ArrayList<EventHeuristics> fromGameState(Empire gameState, int playerId, double discoveredTilesRatio) {
        var possibleActions = gameState.getPossibleActions(playerId);
        var result = new ArrayList<EventHeuristics>(possibleActions.size());
        for (EmpireEvent action : possibleActions) {
            result.add(new EventHeuristics(gameState, action, discoveredTilesRatio));
        }
        return result;
    }

    public static double calculateTotalHeuristic(Empire gameState, EmpireEvent event, double discoveredTilesRatio) {
        var newlyDiscoveredTiles = 0.0;
        if (event instanceof MovementStartOrder movementStartOrder) {
            var destination = movementStartOrder.getDestination();
            var map = gameState.getBoard();
            var unit = gameState.getUnit(movementStartOrder.getUnitId());
            var fov = unit.getFov();

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
            newlyDiscoveredTiles /= 9.0;
        }
        return newlyDiscoveredTiles + UnitHeuristics.calculateUnitHeuristic(gameState, event, discoveredTilesRatio);
    }

    public static EmpireEvent selectBest(Empire gameState, Set<EmpireEvent> possibleActions, double discoveredTilesRatio) {
        EmpireEvent best = null;
        double bestHeuristic = -1;
        for (EmpireEvent event : possibleActions) {
            if (best == null) {
                best = event;
                bestHeuristic = calculateTotalHeuristic(gameState, event, discoveredTilesRatio);
            } else {
                var heuristic = calculateTotalHeuristic(gameState, event, discoveredTilesRatio);
                if (heuristic > bestHeuristic) {
                    bestHeuristic = heuristic;
                    best = event;
                }
            }
        }
        return best;
    }
}
