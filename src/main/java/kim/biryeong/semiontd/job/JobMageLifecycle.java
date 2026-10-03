package kim.biryeong.semiontd.job;

import java.util.UUID;
import kim.biryeong.semiontd.tower.mage.MageStates;
import kim.biryeong.semiontd.tower.mage.MageTowerLifecycle;

final class JobMageLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        MageStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        UUID owner = context.player().uuid();
        context.game().playerLane(owner).ifPresent(lane -> MageTowerLifecycle.finishRound(lane, owner));
    }

    @Override
    public void onEliminated(JobContext context) {
        MageStates.clear(context.player().uuid());
    }
}
