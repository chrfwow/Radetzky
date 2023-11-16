package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

public class UnitHeuristics {

    private static final int[] hpOfType = new int[] {0, 10, 4, 30}; // index 0 is unused, because type id starts at 1
    private static final int[] fovOfType = new int[] {0, 1, 2, 1}; // index 0 is unused, because type id starts at 1
    private static final int[] costOfType = new int[] {0, 10, 10, 20}; // index 0 is unused, because type id starts at 1
    private static final double[] damagePerSecondOfType = new double[] {0, 1.5 * .8, .5 * 1, 3 * .6}; // index 0 is unused, because type id starts at 1
    private static final double[] speedOfType = new double[] {0, .4, .8, .6}; // index 0 is unused, because type id starts at 1
    private static final double maxUnitCost = 20;

    public final double tilesDiscoverCapacity;
    public final double damageCapacity;
    public final double totalHp;


    public UnitHeuristics(Empire gameState, int playerId) {
        var units = gameState.getUnitsByPlayer(playerId);
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
    }

    private static double calculateHeuristicFromUnitType(int unitType, double discoveredTilesRatio) {
        double inverseRatio = 1.0 - discoveredTilesRatio;
        double squaredRatio = discoveredTilesRatio * discoveredTilesRatio;
        double costMalus = costOfType[unitType] / maxUnitCost;
        costMalus *= costMalus;
        return speedOfType[unitType] * fovOfType[unitType] * inverseRatio +
                hpOfType[unitType] * squaredRatio + damagePerSecondOfType[unitType] * squaredRatio - costMalus;
    }

    public static double calculateUnitHeuristic(Empire gameState, EmpireEvent event, double discoveredTilesRatio) {
        if (event instanceof ProductionStartOrder productionStartOrder) {
            return calculateHeuristicFromUnitType(productionStartOrder.getUnitTypeId(), discoveredTilesRatio);
        }
        return 0;
    }
}
