package at.ac.tuwien.ifs.sge.agent;

import java.util.UUID;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.MovementAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.CombatStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.CombatStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.MovementStopOrder;

public final class EmpireEventHelper {
    public static UUID getTaskedUnitIdFromEventOrNull(EmpireEvent event) {
        if (event instanceof MovementStartOrder movementStartOrder) {
            return movementStartOrder.getUnitId();
        } else if (event instanceof MovementAction movementAction) {
            return movementAction.getUnitId();
        } else if (event instanceof MovementStopOrder movementStopOrder) {
            return movementStopOrder.getUnitId();
        } else if (event instanceof CombatStartOrder combatStartOrder) {
            return combatStartOrder.getAttackerId();
        } else if (event instanceof CombatStopOrder combatStopOrder) {
            return combatStopOrder.getAttackerId();
        } else if (event instanceof ProductionStartOrder productionStartOrder) {
            return productionStartOrder.getUnitId();
        }
        return null;
    }
}
