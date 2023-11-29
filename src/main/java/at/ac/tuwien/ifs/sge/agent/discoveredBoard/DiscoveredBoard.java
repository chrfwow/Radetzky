package at.ac.tuwien.ifs.sge.agent.discoveredBoard;

import at.ac.tuwien.ifs.sge.agent.UnitStats;
import at.ac.tuwien.ifs.sge.agent.unitHeuristics.UnitHeuristics;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

public interface DiscoveredBoard {
    DiscoveredBoard copy();

    float getDiscoveredBoardRatio();

    void apply(Empire gameState, EmpireEvent nextAction);

    void advance(long millis, Empire gameState, UnitHeuristics unitHeuristics);

    int getNumberOfNewUndiscoveredTiles(Empire gameState, MovementStartOrder movementStartOrder, EmpireUnit unit);

    int getPlayerId();

    default float calculateHeuristics(Empire gameState, EmpireEvent empireEvent) {
        if (empireEvent instanceof MovementStartOrder movementStartOrder) {
            var unit = gameState.getUnit(movementStartOrder.getUnitId());
            if (unit.getPlayerId() != getPlayerId()) return 0;
            var unitType = unit.getUnitTypeId();
            var newlyDiscoveredTiles = getNumberOfNewUndiscoveredTiles(gameState, movementStartOrder, unit);
            return ((newlyDiscoveredTiles * UnitStats.speedOfType[unitType]) / 9f) *
                    (1f - getDiscoveredBoardRatio()); // the more tiles have been uncovered, the less sense it makes to explore
            // todo can scouts discover more than 9 tiles per move?
        }
        return 0;
    }

    static DiscoveredBoard[] copy(DiscoveredBoard[] immutableBoards) {
        var copy = new DiscoveredBoard[immutableBoards.length];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = immutableBoards[i].copy();
        }
        return copy;
    }
}
