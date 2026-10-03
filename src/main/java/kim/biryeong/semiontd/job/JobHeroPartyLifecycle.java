package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.hero.HeroPartyStates;

final class JobHeroPartyLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        HeroPartyStates.clear(context.player().uuid());
        HeroPartyStates.state(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        HeroPartyStates.assignQuest(context, round);
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        HeroPartyStates.finishQuest(context);
    }

    @Override
    public void onEliminated(JobContext context) {
        HeroPartyStates.clear(context.player().uuid());
    }
}
