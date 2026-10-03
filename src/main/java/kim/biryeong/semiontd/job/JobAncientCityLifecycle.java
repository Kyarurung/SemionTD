package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityStates;

final class JobAncientCityLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        AncientCityStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        AncientCityStates.onRoundStarted(context.player().uuid(), round);
    }

    @Override
    public void onMonsterKilled(JobContext context, Monster monster, long mineralReward) {
        AncientCityStates.onMonsterKilled(context, monster);
    }

    @Override
    public void onEliminated(JobContext context) {
        AncientCityStates.clear(context.player().uuid());
    }
}
