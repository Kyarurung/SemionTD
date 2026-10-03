package kim.biryeong.semiontd.job;

import java.util.UUID;
import kim.biryeong.semiontd.tower.developer.DeveloperPatchService;
import kim.biryeong.semiontd.tower.developer.DeveloperStates;

final class JobDeveloperLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        DeveloperStates.clear(context.player().uuid());
    }

    /**
     * Rolls the per-round budgets.
     *
     * <p>Capacity is recomputed from the lane here rather than tracked as towers are bought and
     * sold, so a player who sold their 운영 센터 loses 긴급 점검 at the start of the next round
     * instead of keeping a budget for a tower that no longer exists.
     */
    @Override
    public void onRoundStarted(JobContext context, int round) {
        UUID playerId = context.player().uuid();
        DeveloperStates.openRound(
                playerId,
                round,
                context.game().playerLane(playerId)
                        .map(lane -> DeveloperPatchService.capacityFor(lane, playerId))
                        .orElse(DeveloperStates.Capacity.none())
        );
    }

    @Override
    public void onEliminated(JobContext context) {
        DeveloperStates.clear(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        DeveloperStates.clear(context.player().uuid());
    }
}
