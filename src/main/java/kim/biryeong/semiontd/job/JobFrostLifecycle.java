package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.frost.FrostTeamEffects;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService;

final class JobFrostLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        kim.biryeong.semiontd.tower.frost.FrostAugments.clearPlayer(context.player().uuid());
        FrostFullOperationService.clearPlayer(context.player().uuid());
        var team = context.game().teams().get(context.player().teamId());
        if (team != null) {
            FrostTeamEffects.registerTeam(context.player().uuid(), team.laneGroup());
        }
    }

    @Override
    public void onEliminated(JobContext context) {
        kim.biryeong.semiontd.tower.frost.FrostAugments.clearPlayer(context.player().uuid());
        FrostTeamEffects.unregisterPlayer(context.player().uuid());
        FrostFullOperationService.clearPlayer(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        kim.biryeong.semiontd.tower.frost.FrostAugments.clearPlayer(context.player().uuid());
        FrostTeamEffects.unregisterPlayer(context.player().uuid());
        FrostFullOperationService.clearPlayer(context.player().uuid());
    }
}
