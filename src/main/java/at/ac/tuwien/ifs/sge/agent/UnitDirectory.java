package at.ac.tuwien.ifs.sge.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final int playerId;

    public UnitDirectory(int playerId) {this.playerId = playerId;}

    public void onGameUpdate(Empire realGame, EmpireEvent action, Logger logger) {
        if (action instanceof InitialSpawnAction initialSpawnAction) {
            if (initialSpawnAction.getPlayerId() != playerId) {
                logger.info("InitialSpawnAction for wrong player");
                return;
            }
            units.add(realGame.getUnit(initialSpawnAction.getUnitId()));
        } else if (action instanceof ProductionStartOrder productionStartOrder) {
            var city = realGame.getCitiesByPosition().get(productionStartOrder.getCityPosition());
            logger.info("ProductionStartOrder for city " + city + " and unit type " + productionStartOrder.getUnitTypeId());
            productions.put(city, new Production(realGame, productionStartOrder.getUnitTypeId()));
        } else if (action instanceof ProductionStopOrder productionStopOrder) {
            var city = realGame.getCitiesByPosition().get(productionStopOrder.getCityPosition());
            logger.info("ProductionStopOrder for city " + city);
            productions.remove(city);
        }
    }

    public void print(Logger log) {
        log.info("Player has " + units.size() + " units, and " + productions.size() + " are being produced");
    }

    public UnitHeuristics getHeuristics() {
        HashMap<EmpireCity, Production> map = new HashMap<>(productions.size());
        for (Map.Entry<EmpireCity, Production> entry : productions.entrySet()) {
            map.put(entry.getKey(), entry.getValue().copy());
        }
        return new UnitHeuristics(new ArrayList<>(units), map);
    }
}
