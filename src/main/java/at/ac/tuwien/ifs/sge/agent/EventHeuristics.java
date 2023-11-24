package at.ac.tuwien.ifs.sge.agent;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitState;

public class EventHeuristics {
    public final EmpireEvent event;
    public final double heuristic;

    // todo use playerId, if other player than -heuristic
    public EventHeuristics(Empire gameState, EmpireEvent event, DiscoveredBoard discoveredBoard) {
        this.event = event;
        this.heuristic = calculateTotalHeuristic(gameState, event, discoveredBoard);
    }

    public static ArrayList<EventHeuristics> fromGameState(Empire gameState, int playerId, DiscoveredBoard discoveredBoard) {
        var possibleActions = gameState.getPossibleActions(playerId);
        var result = new ArrayList<EventHeuristics>(possibleActions.size());
        for (EmpireEvent action : possibleActions) {
            result.add(new EventHeuristics(gameState, action, discoveredBoard));
        }
        return result;
    }

    public static float calculateTotalHeuristic(Empire gameState, EmpireEvent event, DiscoveredBoard discoveredBoard) {
        float heuristic = 0;
        if (event instanceof MovementStartOrder movementStartOrder) {
            var unit = gameState.getUnit(movementStartOrder.getUnitId());
            if (unit.getState() == EmpireUnitState.Moving) return 0; // already moving todo actually useful?

            var unitPosition = unit.getPosition();
            var cityAtStart = gameState.getCity(unitPosition);
            if (cityAtStart != null) {
                var unitsOnCity = cityAtStart.getOccupants().size();
                if (unitsOnCity <= 1) {
                    return -100000; // would abort production of unit
                }
            }

            var destination = movementStartOrder.getDestination();
            var cityAtDestination = gameState.getCity(destination);
            if (cityAtDestination != null) {
                var unitsOnCity = cityAtDestination.getOccupants().size();
                if (unitsOnCity == 0) {
                    return 1000; // occupy unoccupied cities
                }
            }

            var citiesByPos = gameState.getCitiesByPosition();
            if (!citiesByPos.isEmpty()) {
                EmpireCity closest = null;
                var closestDist = 0f;
                for (Map.Entry<Position, EmpireCity> entry : citiesByPos.entrySet()) {
                    var current = entry.getValue();
                    if (current.getOccupants().size() > 0) continue;
                    var distance = PositionExtensions.GetDistance(unitPosition, entry.getKey());
                    if (closest == null) {
                        closest = entry.getValue();
                        closestDist = distance;
                    } else if (distance < closestDist) {
                        closestDist = distance;
                        closest = current;
                    }
                }

                if (closest != null) {
                    var distanceFromStart = closestDist;
                    if (distanceFromStart < .001f) distanceFromStart = .001f; // prevent division by 0
                    var distanceFromDestination = PositionExtensions.GetDistance(destination, closest.getPosition());
                    // reward getting closer to the closest empty city, faster units will be there sooner, so higher reward
                    // delta / distanceFromStart so that closer units get higher reward, otherwise the furthest unit would be sent to city
                    var delta = distanceFromStart - distanceFromDestination;
                    heuristic += (delta / distanceFromStart) * UnitStats.speedOfType[unit.getUnitTypeId()] * 10;
                }
            }
        }
        return heuristic +
                UnitHeuristics.calculateUnitHeuristic(event, discoveredBoard) +
                DiscoveredBoard.calculateHeuristics(gameState, event, discoveredBoard);
    }

    public static EmpireEvent selectBest(Empire gameState, Set<EmpireEvent> possibleActions, DiscoveredBoard discoveredBoard) {
        EmpireEvent best = null;
        double bestHeuristic = -1;
        for (EmpireEvent event : possibleActions) {
            if (best == null) {
                best = event;
                bestHeuristic = calculateTotalHeuristic(gameState, event, discoveredBoard);
            } else {
                var heuristic = calculateTotalHeuristic(gameState, event, discoveredBoard);
                if (heuristic > bestHeuristic) {
                    bestHeuristic = heuristic;
                    best = event;
                }
            }
        }
        return best;
    }
}
