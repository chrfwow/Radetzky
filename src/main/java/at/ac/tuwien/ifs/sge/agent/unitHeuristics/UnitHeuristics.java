package at.ac.tuwien.ifs.sge.agent.unitHeuristics;

import at.ac.tuwien.ifs.sge.agent.UnitStats;
import at.ac.tuwien.ifs.sge.agent.discoveredBoard.DiscoveredBoard;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

public interface UnitHeuristics {
    UnitHeuristics copy();

    void apply(Empire gameState, EmpireEvent action);

    void advance(Empire gameState,long millis);

    float calculatePostSimulation(int playerId, Empire gameState);

    float getTilesDiscoveryCapacity();

    static float getTilesDiscoveryCapacity(int unitType) {
        return UnitStats.speedOfType[unitType] * getTilesDiscoveryCapacityFromFov(UnitStats.fovOfType[unitType]);
    }

    static float getTilesDiscoveryCapacity(EmpireUnit unit) {
        return (float) (unit.getTilesPerSecond()) * getTilesDiscoveryCapacityFromFov(unit.getFov());
    }

    static int getTilesDiscoveryCapacityFromFov(int fov) {
        var diameter = fov * 2 + 1;
        var overlappingDiameter = diameter - 1;
        return diameter * diameter - overlappingDiameter * overlappingDiameter;
    }

    static float getDamageCapacity(int unitType) {
        return UnitStats.damagePerSecondOfType[unitType];
    }

    static float getDamageCapacity(EmpireUnit unit) {
        return getDamageCapacity(unit.getUnitTypeId());
    }

    static float calculateHeuristicFromUnitType(int unitType, DiscoveredBoard discoveredBoard) {
        var discoveredTilesRatio = discoveredBoard.getDiscoveredBoardRatio();
        float inverseRatio = 1f - discoveredTilesRatio;
        float squaredRatio = discoveredTilesRatio * discoveredTilesRatio;
        float costMalus = UnitStats.costOfType[unitType] / UnitStats.maxUnitCost;
        costMalus *= costMalus;
        return getTilesDiscoveryCapacity(unitType) * inverseRatio + // make fast units in the beginning
                (UnitStats.hpOfType[unitType] / UnitStats.maxHp) * squaredRatio +  // make strong units in the end
                getDamageCapacity(unitType) * squaredRatio - costMalus;
    }

    static float calculateHeuristicFromUnitType(EmpireUnit unit, DiscoveredBoard discoveredBoard) {
        var unitType = unit.getUnitTypeId();
        var discoveredTilesRatio = discoveredBoard.getDiscoveredBoardRatio();
        float inverseRatio = 1f - discoveredTilesRatio;
        float squaredRatio = discoveredTilesRatio * discoveredTilesRatio;
        float costMalus = UnitStats.costOfType[unitType] / UnitStats.maxUnitCost;
        costMalus *= costMalus;
        return getTilesDiscoveryCapacity(unit) * inverseRatio + // make fast units in the beginning
                (unit.getHp() / UnitStats.maxHp) * squaredRatio +  // make strong units in the end
                getDamageCapacity(unitType) * squaredRatio - costMalus;
    }

    static float calculateUnitHeuristic(EmpireEvent event, DiscoveredBoard discoveredBoard) {
        if (event instanceof ProductionStopOrder) {
            return -10000000f; // would abort production of unit with no gain
        } else if (event instanceof ProductionStartOrder productionStartOrder) {
            return 10f * calculateHeuristicFromUnitType(productionStartOrder.getUnitTypeId(), discoveredBoard);
        }
        return 0;
    }

    static UnitHeuristics[] copy(UnitHeuristics[] immutableBoards) {
        var copy = new UnitHeuristics[immutableBoards.length];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = immutableBoards[i].copy();
        }
        return copy;
    }
}
