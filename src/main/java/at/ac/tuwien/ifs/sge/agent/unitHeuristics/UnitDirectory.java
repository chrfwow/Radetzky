package at.ac.tuwien.ifs.sge.agent.unitHeuristics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.ac.tuwien.ifs.sge.agent.UnitStats;
import at.ac.tuwien.ifs.sge.agent.discoveredBoard.RadetzkyDiscoveredBoard;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.InitialSpawnAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

public class UnitDirectory {

    private final List<EmpireUnit> units = new ArrayList<>();
    private final Map<EmpireCity, Production> productions = new HashMap<>();
    private final int radetzkyPlayerId;

    public UnitDirectory(int radetzkyPlayerId) {this.radetzkyPlayerId = radetzkyPlayerId;}

    public void onGameUpdate(Empire realGame, EmpireEvent action, Logger logger) {
        if (action instanceof InitialSpawnAction initialSpawnAction) {
            if (initialSpawnAction.getPlayerId() != radetzkyPlayerId) return;
            synchronized (units) {
                units.add(realGame.getUnit(initialSpawnAction.getUnitId()));
            }
        } else if (action instanceof ProductionStartOrder productionStartOrder) {
            var city = realGame.getCitiesByPosition().get(productionStartOrder.getCityPosition());
            synchronized (productions) {
                productions.put(city, new Production(realGame, productionStartOrder.getUnitTypeId()));
            }
        } else if (action instanceof ProductionStopOrder productionStopOrder) {
            var city = realGame.getCitiesByPosition().get(productionStopOrder.getCityPosition());
            synchronized (productions) {
                productions.remove(city);
            }
        }
    }

    public void print(Logger log) {
        log.info("Player has " + units.size() + " units, and " + productions.size() + " are being produced");
    }

    public RadetzkyUnitHeuristics getHeuristics(RadetzkyDiscoveredBoard discoveredBoard) {
        HashMap<EmpireCity, Production> inProduction;
        synchronized (productions) {
            inProduction = new HashMap<>(productions.size());
            for (Map.Entry<EmpireCity, Production> entry : productions.entrySet()) {
                inProduction.put(entry.getKey(), entry.getValue().copy());
            }
        }
        List<EmpireUnit> unitsCopy;
        synchronized (units) {
            unitsCopy = new ArrayList<>(units);
        }
        return new RadetzkyUnitHeuristics(radetzkyPlayerId, unitsCopy, inProduction, discoveredBoard);
    }


    public static int getBestCurrentUnitType(RadetzkyDiscoveredBoard discoveredBoard) {
        var bestType = 1;
        var bestHeuristic = UnitHeuristics.calculateHeuristicFromUnitType(bestType, discoveredBoard);
        for (int i = 2; i < UnitStats.costOfType.length; i++) {
            var current = UnitHeuristics.calculateHeuristicFromUnitType(i, discoveredBoard);
            if (current > bestHeuristic) {
                bestHeuristic = current;
                bestType = i;
            }
        }
        return bestType;
    }
}
