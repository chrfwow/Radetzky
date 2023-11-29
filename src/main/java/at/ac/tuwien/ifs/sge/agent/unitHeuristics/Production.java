package at.ac.tuwien.ifs.sge.agent.unitHeuristics;

import at.ac.tuwien.ifs.sge.agent.UnitStats;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

public class Production {
    public final int type;
    public final long finishTime;
    public boolean isFinished;

    public Production(Empire gameState, int type) {
        this.type = type;
        this.finishTime = gameState.getGameClock().getGameTimeMs() + UnitStats.costOfType[type];
    }

    private Production(int type, long finishTime, boolean isFinished) {
        this.type = type;
        this.finishTime = finishTime;
        this.isFinished = isFinished;
    }

    public Production copy() {
        return new Production(type, finishTime, isFinished);
    }

    public void advance(RadetzkyUnitHeuristics unitHeuristics, long now) {
        if (isFinished) return;
        if (now >= finishTime) {
            isFinished = true;
            unitHeuristics.tilesDiscoverCapacity += UnitStats.fovOfType[type] * UnitStats.speedOfType[type];
            unitHeuristics.damageCapacity += UnitStats.damagePerSecondOfType[type];
            unitHeuristics.totalHp += UnitStats.hpOfType[type];
        }
    }

    public float getFinishedRatio(long now) {
        var remaining = finishTime - now;
        if (remaining <= 0) return 1f;
        return (float) remaining / UnitStats.costOfType[type];
    }
}
