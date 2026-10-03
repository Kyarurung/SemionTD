package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.tower.pirate.PirateTower;
import kim.biryeong.semiontd.tower.pirate.PirateStates;

final class JobPirateLifecycle implements JobLifecycle {
    @Override public void onMatchStarted(JobContext context) { PirateStates.open(context.game(), context.player()); }

    @Override public void onRoundStarted(JobContext context, int round) { PirateStates.startRound(context.player().uuid()); }

    @Override public void onRoundEnded(JobContext context, int round) { context.game().playerLane(context.player().uuid()).ifPresent(lane -> List.copyOf(lane.towers()).stream().filter(PirateTower.class::isInstance).map(PirateTower.class::cast).filter(tower -> context.player().uuid().equals(tower.ownerPlayer())).forEach(tower -> tower.onRoundEnded(lane, round))); }

    @Override public void onEliminated(JobContext context) { PirateStates.close(context.player().uuid()); }

    @Override public void onMatchClosed(JobContext context) { PirateStates.close(context.player().uuid()); }
}
