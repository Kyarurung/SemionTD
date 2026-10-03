package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.atlantis.AtlantisPressure;
import kim.biryeong.semiontd.tower.atlantis.AtlantisStates;

final class JobAtlantisLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        clearState(context);
    }

    @Override
    public void onEliminated(JobContext context) {
        clearState(context);
    }

    private static void clearState(JobContext context) {
        AtlantisStates.clear(context.player().uuid());
        AtlantisPressure.clearPlayer(context.player().uuid());
    }
}
