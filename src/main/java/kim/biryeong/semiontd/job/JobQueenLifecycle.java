package kim.biryeong.semiontd.job;

import java.util.UUID;
import kim.biryeong.semiontd.tower.queen.QueenStates;

final class JobQueenLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        UUID playerId = context.player().uuid();
        QueenStates.begin(playerId, context.game().teams().get(context.player().teamId()).laneGroup());
    }

    @Override public void onEliminated(JobContext context) {QueenStates.clear(context.player().uuid());}
}
