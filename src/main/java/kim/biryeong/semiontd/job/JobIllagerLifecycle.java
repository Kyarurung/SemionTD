package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;

final class JobIllagerLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        IllagerRaidStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        IllagerRaidStates.onRoundStarted(context);
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        IllagerRaidStates.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        IllagerRaidStates.clear(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        IllagerRaidStates.clear(context.player().uuid());
    }
}
