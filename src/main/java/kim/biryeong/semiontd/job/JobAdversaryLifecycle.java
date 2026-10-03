package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.adversary.AdversaryFoxTower;
import kim.biryeong.semiontd.tower.adversary.AdversaryProgressStates;
import kim.biryeong.semiontd.tower.adversary.AdversaryTeamEffects;

final class JobAdversaryLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        AdversaryProgressStates.clear(context.player().uuid());
        var team = context.game().teams().get(context.player().teamId());
        if (team != null) {
            AdversaryTeamEffects.registerTeam(context.player().uuid(), team.laneGroup());
        }
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        context.game().playerLane(context.player().uuid())
                .ifPresent(lane -> AdversaryProgressStates.reconcileLane(context.player().uuid(), lane));
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        context.game().playerLane(context.player().uuid())
                .ifPresent(lane -> lane.towers().stream()
                        .filter(AdversaryFoxTower.class::isInstance)
                        .map(AdversaryFoxTower.class::cast)
                        .filter(tower -> context.player().uuid().equals(tower.ownerPlayer()))
                        .forEach(tower -> AdversaryProgressStates.state(context.player().uuid())
                                .recordCompletedWave(tower.foxId(), tower.form())));
    }

    @Override
    public void onEliminated(JobContext context) {
        AdversaryTeamEffects.unregisterPlayer(context.player().uuid());
        AdversaryProgressStates.clear(context.player().uuid());
    }
}
