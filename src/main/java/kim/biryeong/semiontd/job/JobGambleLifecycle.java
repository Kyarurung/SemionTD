package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.gamble.GambleRoundEffects;
import kim.biryeong.semiontd.tower.gamble.GambleSpectatorRewards;

final class JobGambleLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        GambleSpectatorRewards.closeRound(context.player().uuid());
        clear(context);
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        GambleSpectatorRewards.openRound(context.player().uuid(), context.player().economy());
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        clear(context);
        GambleSpectatorRewards.closeRound(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        clear(context);
        GambleSpectatorRewards.closeRound(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        clear(context);
        GambleSpectatorRewards.closeRound(context.player().uuid());
    }

    private static void clear(JobContext context) {
        kim.biryeong.semiontd.ui.GambleRevealService.clear(context.player().uuid());
        context.game().playerLane(context.player().uuid())
                .ifPresent(lane -> GambleRoundEffects.clearAll(lane, context.player().uuid()));
    }
}
