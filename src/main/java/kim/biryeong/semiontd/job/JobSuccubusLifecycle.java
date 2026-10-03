package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.succubus.SuccubusAbsorption;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;

final class JobSuccubusLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {clear(context);}

    @Override
    public void onEliminated(JobContext context) {clear(context);}

    @Override
    public void onMatchClosed(JobContext context) {clear(context);}

    private static void clear(JobContext context) {
        SuccubusDreams.clearPlayer(context.player().uuid());
        SuccubusAbsorption.clear(context.player().uuid());
    }
}
