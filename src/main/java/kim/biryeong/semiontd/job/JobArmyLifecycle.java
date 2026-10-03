package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.tower.army.ArmyStates;
import kim.biryeong.semiontd.tower.army.ArmyTower;

final class JobArmyLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        ArmyStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        ArmyStates.beginRound(context.player().uuid(), round);
    }

    /**
     * Discharges everyone who has served their time.
     *
     * <p>Runs here rather than inside the tower because crediting the payout needs the player's
     * economy, and a tower only ever sees its lane. Done between rounds so a tower never vanishes
     * mid-wave, which would read as the tower having been destroyed.
     */
    @Override
    public void onRoundEnded(JobContext context, int round) {
        context.game().playerLane(context.player().uuid()).ifPresent(lane -> {
            List<ArmyTower> towers = lane.towers().stream()
                    .filter(ArmyTower.class::isInstance)
                    .map(ArmyTower.class::cast)
                    .filter(tower -> context.player().uuid().equals(tower.ownerPlayer()))
                    .toList();
            towers.forEach(tower -> tower.completeServiceWave(lane));

            List<ArmyTower> due = towers.stream()
                    .filter(ArmyTower::dischargePending)
                    .toList();

            long payout = 0L;
            for (ArmyTower tower : due) {
                long refund = tower.sellRefundAmount();
                tower.showDebugVfx(lane, ArmyTower.DebugVfx.DISCHARGE);
                if (!lane.removeTower(tower)) {
                    continue;
                }
                payout += refund;
                tower.completeDischarge(lane);
            }
            if (payout > 0L) {
                context.player().economy().addMineral(payout);
            }
        });
    }

    @Override
    public void onEliminated(JobContext context) {
        ArmyStates.clear(context.player().uuid());
    }
}
