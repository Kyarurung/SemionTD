package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.insect.InsectAugments;

final class JobInsectLifecycle implements JobLifecycle {
    @Override public void onMatchStarted(JobContext context) {InsectAugments.clear(context.player().uuid());}

    @Override public void onRoundEnded(JobContext context, int round) {InsectAugments.clear(context.player().uuid());}

    @Override public void onEliminated(JobContext context) {InsectAugments.clear(context.player().uuid());}

    @Override public void onMatchClosed(JobContext context) {InsectAugments.clear(context.player().uuid());}
}
