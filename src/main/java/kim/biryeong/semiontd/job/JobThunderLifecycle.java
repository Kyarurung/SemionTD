package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.thunder.ThunderStates;

final class JobThunderLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        ThunderStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        // One shared roll per wave: a thunderstorm covers the whole lane, so every storm rod the
        // player owns reports the same output rather than each rolling independently.
        ThunderStates.rollStorm(context.player().uuid(), round);
    }

    @Override
    public void onEliminated(JobContext context) {
        ThunderStates.clear(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        ThunderStates.clear(context.player().uuid());
    }
}
