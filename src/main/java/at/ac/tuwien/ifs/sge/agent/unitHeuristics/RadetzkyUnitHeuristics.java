package at.ac.tuwien.ifs.sge.agent.unitHeuristics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.ac.tuwien.ifs.sge.agent.UnitStats;
import at.ac.tuwien.ifs.sge.agent.discoveredBoard.RadetzkyDiscoveredBoard;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.ProductionAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

public class RadetzkyUnitHeuristics implements UnitHeuristics {
    private final int radetzkyPlayerId;
    private final RadetzkyDiscoveredBoard discoveredBoard;
    public float tilesDiscoverCapacity;
    public float damageCapacity;
    public float totalHp;
    private final Map<EmpireCity, Production> inProduction; // todo remove?

    public RadetzkyUnitHeuristics(int radetzkyPlayerId, List<EmpireUnit> units, Map<EmpireCity, Production> inProduction, RadetzkyDiscoveredBoard discoveredBoard) {
        this.radetzkyPlayerId = radetzkyPlayerId;
        this.discoveredBoard = discoveredBoard;
        var tilesDiscoverCapacity = 0f;
        var damageCapacity = 0f;
        var totalHp = 0f;

        for (int i = 0; i < units.size(); i++) {
            var current = units.get(i);
            tilesDiscoverCapacity += UnitHeuristics.getTilesDiscoveryCapacity(current);
            damageCapacity += UnitHeuristics.getDamageCapacity(current);
            totalHp += current.getHp();
        }

        this.tilesDiscoverCapacity = tilesDiscoverCapacity;
        this.damageCapacity = damageCapacity;
        this.totalHp = totalHp;
        this.inProduction = inProduction;
    }

    private RadetzkyUnitHeuristics(int radetzkyPlayerId, float tilesDiscoverCapacity, float damageCapacity, float totalHp, Map<EmpireCity, Production> inProduction, RadetzkyDiscoveredBoard discoveredBoard) {
        this.radetzkyPlayerId = radetzkyPlayerId;
        this.tilesDiscoverCapacity = tilesDiscoverCapacity;
        this.damageCapacity = damageCapacity;
        this.totalHp = totalHp;
        this.inProduction = new HashMap<>(inProduction);
        this.discoveredBoard = discoveredBoard;
    }

    @Override
    public RadetzkyUnitHeuristics copy() {
        return new RadetzkyUnitHeuristics(radetzkyPlayerId, tilesDiscoverCapacity, damageCapacity, totalHp, inProduction, discoveredBoard.copy());
    }

    @Override
    public void apply(Empire gameState, EmpireEvent action) {
        if (action instanceof ProductionStartOrder productionStartOrder) {
            var city = gameState.getCitiesByPosition().get(productionStartOrder.getCityPosition());
            var type = productionStartOrder.getUnitTypeId();
            inProduction.put(city, new Production(gameState, type));
        } else if (action instanceof ProductionStopOrder productionStopOrder) {
            var city = gameState.getCitiesByPosition().get(productionStopOrder.getCityPosition());
            inProduction.remove(city);
        } else if (action instanceof ProductionAction productionAction) {
            var unit = gameState.getUnit(productionAction.getUnitId());
            if (unit.getPlayerId() != radetzkyPlayerId) return;
            tilesDiscoverCapacity += UnitHeuristics.getTilesDiscoveryCapacity(unit);
            damageCapacity += UnitHeuristics.getDamageCapacity(unit);
        }
    }

    @Override
    public void advance(Empire gameState, long millis) {
        if (inProduction.isEmpty()) return;
        inProduction.keySet().removeIf(city -> city.getOccupants().isEmpty());
        var now = gameState.getGameClock().getGameTimeMs();
        inProduction.entrySet().removeIf(entry -> {
            var production = entry.getValue();
            production.advance(this, now);
            return production.isFinished;
        });
    }

    @Override
    public float calculatePostSimulation(int playerId, Empire gameState) {
        var heuristic = 0f;
        var units = gameState.getUnitsByPlayer(playerId);
        for (int i = 0; i < units.size(); i++) {
            var unit = units.get(i);
            heuristic += UnitHeuristics.calculateHeuristicFromUnitType(unit, discoveredBoard);
        }

        var now = gameState.getGameClock().getGameTimeMs();
        for (Production production : inProduction.values()) {
            heuristic += UnitHeuristics.calculateHeuristicFromUnitType(production.type, discoveredBoard) * production.getFinishedRatio(now);
        }
        return heuristic;
    }

    @Override
    public float getTilesDiscoveryCapacity() {
        return tilesDiscoverCapacity;
    }
}
