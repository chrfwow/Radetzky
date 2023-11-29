package at.ac.tuwien.ifs.sge.agent.unitHeuristics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import at.ac.tuwien.ifs.sge.agent.UnitStats;
import at.ac.tuwien.ifs.sge.agent.discoveredBoard.EnemyDiscoveredBoard;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.UnitAppearedAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.UnitDamagedAction;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

public class EnemyUnitHeuristics implements UnitHeuristics {
    // todo remove. For now, we assume that the enemy AI is not perfect either and reduce its max efficiency
    public static final float ArtificialInefficiencyFactor = .7f;
    private final int playerId;
    private final EnemyDiscoveredBoard discoveredBoard;

    private final HashMap<UUID, EmpireUnit> knownUnits = new HashMap<>();
    private final int[] estimatedNumberOfUnitsPerType = new int[4]; // [0] unused
    private long timeOfLastProduction = 0;

    public EnemyUnitHeuristics(int playerId, EnemyDiscoveredBoard discoveredBoard) {
        this.playerId = playerId;
        this.discoveredBoard = discoveredBoard;
    }

    private EnemyUnitHeuristics(int playerId, EnemyDiscoveredBoard discoveredBoard, long timeOfLastProduction, HashMap<UUID, EmpireUnit> knownUnits, int[] estimatedNumberOfUnitsPerType) {
        this.playerId = playerId;
        this.discoveredBoard = discoveredBoard;
        this.timeOfLastProduction = timeOfLastProduction;
        this.knownUnits.putAll(knownUnits);
        System.arraycopy(estimatedNumberOfUnitsPerType, 1, this.estimatedNumberOfUnitsPerType, 1, estimatedNumberOfUnitsPerType.length - 1);
    }

    @Override
    public UnitHeuristics copy() {
        return new EnemyUnitHeuristics(playerId, discoveredBoard.copy(), timeOfLastProduction, knownUnits, estimatedNumberOfUnitsPerType);
    }

    @Override
    public void apply(Empire gameState, EmpireEvent action) {
        if (action instanceof UnitAppearedAction unitAppearedAction) {
            var unit = unitAppearedAction.getUnit();
            if (unit.getPlayerId() != playerId) return;
            knownUnits.putIfAbsent(unit.getId(), unit);
            var type = unit.getUnitTypeId();
            removeEstimatedUnit(type);
        } else if (action instanceof UnitDamagedAction unitDamagedAction) {
            var unit = gameState.getUnit(unitDamagedAction.getTargetId());
            onUnitDamaged(unit);
        }
    }

    private void onUnitDamaged(EmpireUnit unit) {
        if (unit == null) return;
        if (unit.getPlayerId() != playerId) return;
        if (unit.isAlive()) return;
        if (knownUnits.remove(unit.getId()) == null) return;
        removeEstimatedUnit(unit.getUnitTypeId());
    }

    private void removeEstimatedUnit(int type) {
        estimatedNumberOfUnitsPerType[type]--;
        if (estimatedNumberOfUnitsPerType[type] < 0) {
            estimatedNumberOfUnitsPerType[type] = 0;

            for (int i = 1; i < estimatedNumberOfUnitsPerType.length; i++) {
                if (i == type) continue;
                if (estimatedNumberOfUnitsPerType[i] <= 0) continue;
                estimatedNumberOfUnitsPerType[i]--;
                break;
            }
        }
    }

    @Override
    public void advance(Empire gameState, long millis) {
        var bestType = getCurrentBestUnitType();
        timeOfLastProduction += millis * discoveredBoard.getNumberOfKnownAndEstimatedCities();
        var bestCost = UnitStats.costOfType[bestType] * 1000;
        while (timeOfLastProduction >= bestCost) {
            timeOfLastProduction -= bestCost;
            estimatedNumberOfUnitsPerType[bestType]++;
        }
    }

    private int getCurrentBestUnitType() {
        var bestType = 1;
        var bestHeuristic = UnitHeuristics.calculateHeuristicFromUnitType(bestType, discoveredBoard);
        for (int i = 2; i < estimatedNumberOfUnitsPerType.length; i++) {
            var current = UnitHeuristics.calculateHeuristicFromUnitType(i, discoveredBoard);
            if (current > bestHeuristic) {
                bestHeuristic = current;
                bestType = i;
            }
        }
        return bestType;
    }

    @Override
    public float calculatePostSimulation(int playerId, Empire gameState) {
        var heuristic = 0f;
        for (int i = 1; i < estimatedNumberOfUnitsPerType.length; i++) {
            heuristic += UnitHeuristics.calculateHeuristicFromUnitType(i, discoveredBoard) * estimatedNumberOfUnitsPerType[i];
        }
        var bestType = getCurrentBestUnitType();
        var bestTypeCost = UnitStats.costOfType[bestType] * 1000f;
        var affordableFraction = timeOfLastProduction / bestTypeCost;
        heuristic += UnitHeuristics.calculateHeuristicFromUnitType(bestType, discoveredBoard) * affordableFraction;

        for (Map.Entry<UUID, EmpireUnit> entry : knownUnits.entrySet()) {
            var unit = entry.getValue();
            heuristic += UnitHeuristics.calculateHeuristicFromUnitType(unit, discoveredBoard);
        }

        return heuristic * ArtificialInefficiencyFactor;
    }

    @Override
    public float getTilesDiscoveryCapacity() {
        var capacity = 0f;
        for (int i = 1; i < estimatedNumberOfUnitsPerType.length; i++) {
            capacity += UnitHeuristics.getTilesDiscoveryCapacity(i);
        }
        for (Map.Entry<UUID, EmpireUnit> entry : knownUnits.entrySet()) {
            var unit = entry.getValue();
            capacity += UnitHeuristics.getTilesDiscoveryCapacity(unit);
        }
        return capacity * ArtificialInefficiencyFactor;
    }
}
