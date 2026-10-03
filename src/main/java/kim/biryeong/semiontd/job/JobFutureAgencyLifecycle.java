package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.futureagency.FutureAgencyStates;

final class JobFutureAgencyLifecycle implements JobLifecycle {
    @Override public void onMatchStarted(JobContext context) {FutureAgencyStates.clear(context.player().uuid());}

    @Override public void onRoundStarted(JobContext context, int round) {FutureAgencyStates.state(context.player().uuid()).openRound(round);}

    @Override public void onRoundEnded(JobContext context, int round) {
        FutureAgencyStates.state(context.player().uuid())
                .setNextSelectionLimit(context.game().hasClearedRound(context.player().uuid(), round) ? 2 : 1);
    }

    @Override public void onEliminated(JobContext context) {FutureAgencyStates.clear(context.player().uuid());}
}
