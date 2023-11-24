package at.ac.tuwien.ifs.sge.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

public class UnitHeuristics {
    double tilesDiscoverCapacity;
    double damageCapacity;
    double totalHp;
    private final Map<EmpireCity, Production> inProduction; // todo remove?

    public UnitHeuristics(List<EmpireUnit> units, Map<EmpireCity, Production> inProduction) {
        var tilesDiscoverCapacity = 0.0;
        var damageCapacity = 0.0;
        var totalHp = 0.0;

        for (int i = 0; i < units.size(); i++) {
            var current = units.get(i);
            tilesDiscoverCapacity += current.getFov() * current.getTilesPerSecond();
            damageCapacity += current.getHitsPerSecond();
            totalHp += current.getHp();
        }

        this.tilesDiscoverCapacity = tilesDiscoverCapacity;
        this.damageCapacity = damageCapacity;
        this.totalHp = totalHp;
        this.inProduction = inProduction;
    }

    private UnitHeuristics(double tilesDiscoverCapacity, double damageCapacity, double totalHp, Map<EmpireCity, Production> inProduction) {
        this.tilesDiscoverCapacity = tilesDiscoverCapacity;
        this.damageCapacity = damageCapacity;
        this.totalHp = totalHp;
        this.inProduction = new HashMap<>(inProduction);
    }

    public UnitHeuristics copy() {
        return new UnitHeuristics(tilesDiscoverCapacity, damageCapacity, totalHp, inProduction);
    }

    public void apply(Empire gameState, EmpireEvent action) {
        if (action instanceof ProductionStartOrder productionStartOrder) {
            var city = gameState.getCitiesByPosition().get(productionStartOrder.getCityPosition());
            var type = productionStartOrder.getUnitTypeId();
            inProduction.put(city, new Production(gameState, type));
        } else if (action instanceof ProductionStopOrder productionStopOrder) {
            var city = gameState.getCitiesByPosition().get(productionStopOrder.getCityPosition());
            inProduction.remove(city);
        }
    }

    public void advance(Empire gameState) {
        if (inProduction.isEmpty()) return;
        inProduction.keySet().removeIf(city -> city.getOccupants().isEmpty());
        var now = gameState.getGameClock().getGameTimeMs();
        inProduction.entrySet().removeIf(entry -> {
            var production = entry.getValue();
            production.advance(this, now);
            return production.isFinished;
        });
    }

    private static float calculateHeuristicFromUnitType(int unitType, DiscoveredBoard discoveredBoard) {
        var discoveredTilesRatio = discoveredBoard.getDiscoveredBoardRatio();
        float inverseRatio = 1f - discoveredTilesRatio;
        float squaredRatio = discoveredTilesRatio * discoveredTilesRatio;
        float costMalus = UnitStats.costOfType[unitType] / UnitStats.maxUnitCost;
        costMalus *= costMalus;
        return UnitStats.speedOfType[unitType] * UnitStats.fovOfType[unitType] * inverseRatio + // make fast units in the beginning
                (UnitStats.hpOfType[unitType] / UnitStats.maxHp) * squaredRatio +  // make strong units in the end
                UnitStats.damagePerSecondOfType[unitType] * squaredRatio - costMalus;
    }

    public static float calculateUnitHeuristic(EmpireEvent event, DiscoveredBoard discoveredBoard) {
        if (event instanceof ProductionStopOrder) {
            return -100f; // would abort production of unit with no gain
        } else if (event instanceof ProductionStartOrder productionStartOrder) {
            return 10f * calculateHeuristicFromUnitType(productionStartOrder.getUnitTypeId(), discoveredBoard);
        }
        return 0;
    }

    public boolean isCityProducing(EmpireCity city) {
        var producing = inProduction.getOrDefault(city, null);
        if (producing == null) return false;
        return !producing.isFinished;
    }
}
