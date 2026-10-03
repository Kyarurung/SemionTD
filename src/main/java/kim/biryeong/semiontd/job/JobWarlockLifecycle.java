package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.tower.warlock.WarlockAwakeningProgress;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;

final class JobWarlockLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        clearProgress(context);
    }

    @Override
    public void onMonsterKilled(JobContext context, Monster monster, long mineralReward) {
        if (!WarlockAwakeningProgress.recordKill(context.player().uuid())) {
            return;
        }
        context.game().playerLane(context.player().uuid())
                .ifPresent(lane -> WarlockTower.onAwakeningUnlocked(lane, context.player().uuid()));
    }

    @Override
    public void onEliminated(JobContext context) {
        clearProgress(context);
    }

    @Override
    public void onMatchClosed(JobContext context) {
        clearProgress(context);
    }

    private static void clearProgress(JobContext context) {
        WarlockAwakeningProgress.clear(context.player().uuid());
    }
}
