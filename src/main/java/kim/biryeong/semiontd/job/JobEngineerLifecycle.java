package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.engineer.EngineerPressStates;

final class JobEngineerLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        EngineerPressStates.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        EngineerPressStates.clear(context.player().uuid());
    }
}
