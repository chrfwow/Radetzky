package at.ac.tuwien.ifs.sge.agent.discoveredBoard;

import at.ac.tuwien.ifs.sge.agent.UnitStats;
import at.ac.tuwien.ifs.sge.agent.unitHeuristics.EnemyUnitHeuristics;
import at.ac.tuwien.ifs.sge.agent.unitHeuristics.UnitHeuristics;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

public class EnemyDiscoveredBoard implements DiscoveredBoard {

    private final int playerId;
    private final int tilesCount;
    private final float ratioPerTile;
    private float discoveredBoardRatio;

    private int estimatedDiscoveredCities;
    private int knownDiscoveredCities;

    private final int estimatedNumberOfCities;

    private EnemyDiscoveredBoard(Empire game, int playerId, int tilesCount) {
        this.playerId = playerId;
        this.tilesCount = tilesCount;
        this.ratioPerTile = 1f / tilesCount;
        var config = game.getGameConfiguration().getGeneratorConfig();
        var cityC = config.getCityC();
        var numberOfPlayers = game.getNumberOfPlayers();
        var numberOfCityEstimate = (int) (cityC * tilesCount);
        var maxCities = numberOfPlayers * config.getMaxCitiesPerPlayer();
        if (numberOfCityEstimate > maxCities) {
            numberOfCityEstimate = maxCities;
        }
        this.estimatedNumberOfCities = numberOfCityEstimate;
    }

    private EnemyDiscoveredBoard(
            int playerId,
            int tilesCount,
            float ratioPerTile,
            float discoveredBoardRatio,
            int estimatedDiscoveredCities,
            int knownDiscoveredCities,
            int estimatedNumberOfCities
    ) {
        this.playerId = playerId;
        this.tilesCount = tilesCount;
        this.ratioPerTile = ratioPerTile;
        this.discoveredBoardRatio = discoveredBoardRatio;
        this.estimatedDiscoveredCities = estimatedDiscoveredCities;
        this.estimatedNumberOfCities = estimatedNumberOfCities;
        this.knownDiscoveredCities = knownDiscoveredCities;
    }

    public static EnemyDiscoveredBoard get(int playerId, Empire gameState) {
        var map = gameState.getBoard();
        var size = map.getMapSize();
        var discoveredBoard = new EnemyDiscoveredBoard(gameState, playerId, size.getHeight() * size.getWidth());
        var viewDiameter = UnitStats.fovOfType[1] * 2 + 1;
        discoveredBoard.discoveredBoardRatio = discoveredBoard.ratioPerTile * viewDiameter * viewDiameter;
        return discoveredBoard;
    }

    @Override
    public EnemyDiscoveredBoard copy() {
        return new EnemyDiscoveredBoard(playerId, tilesCount, ratioPerTile, discoveredBoardRatio, estimatedDiscoveredCities, knownDiscoveredCities, estimatedNumberOfCities);
    }

    @Override
    public float getDiscoveredBoardRatio() {
        return discoveredBoardRatio;
    }

    @Override
    public void apply(Empire gameState, EmpireEvent nextAction) {
        if (nextAction instanceof MovementStartOrder movementStartOrder) {
            var unit = gameState.getUnit(movementStartOrder.getUnitId());
            if (unit.getPlayerId() != playerId) return;
            var newlyDiscoveredTiles = getNumberOfNewUndiscoveredTiles(gameState, movementStartOrder, unit);
            discoveredBoardRatio += ratioPerTile * newlyDiscoveredTiles;
        }
    }

    @Override
    public void advance(long millis, Empire gameState, UnitHeuristics unitHeuristics) {
        var capacity = unitHeuristics.getTilesDiscoveryCapacity();
        var seconds = millis / 1e3f;
        discoveredBoardRatio += (1f - discoveredBoardRatio) * (ratioPerTile * capacity * seconds * EnemyUnitHeuristics.ArtificialInefficiencyFactor);
        estimatedDiscoveredCities = (int) (discoveredBoardRatio * estimatedNumberOfCities * EnemyUnitHeuristics.ArtificialInefficiencyFactor);
    }

    @Override
    public int getNumberOfNewUndiscoveredTiles(Empire gameState, MovementStartOrder movementStartOrder, EmpireUnit unit) {
        float potential = UnitHeuristics.getTilesDiscoveryCapacityFromFov(unit.getFov()) * (float) unit.getTilesPerSecond();
        return (int) ((1f - discoveredBoardRatio) * potential);
    }

    @Override
    public int getPlayerId() {
        return playerId;
    }
}
