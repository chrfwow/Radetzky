import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;

public class Radetzky<G extends RealTimeGame<A, ?>, A> extends AbstractRealTimeGameAgent<G, A> {
    public Radetzky(Class<G> gameClass, int playerId, String playerName) {
        super(gameClass, playerId, playerName);
    }

    public static void main(String[] args) {
        var playerId = getPlayerIdFromArgs(args);
        var playerName = getPlayerNameFromArgs(args);
        var gameClass = ( Class<? extends RealTimeGame<Object, Object>> ) getGameClassFromArgs(args);
        var agent = new Radetzky<>(gameClass, playerId, playerName);
        agent.start();
    }

    @Override
    protected void onGameUpdate(A action, ActionResult result) {

    }

    @Override
    protected void onActionRejected(A action) {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void startPlaying() {
        super.startPlaying();
    }
}
