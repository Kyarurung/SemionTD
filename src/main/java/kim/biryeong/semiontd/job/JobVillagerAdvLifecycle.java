package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;

final class JobVillagerAdvLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        VillagerAdvStates.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        VillagerAdvStates.clear(context.player().uuid());
    }
}
