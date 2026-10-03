package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.plant.PlantCombatTower;
import kim.biryeong.semiontd.tower.plant.PlantSoilStates;

final class JobPlantLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        PlantSoilStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        context.game().playerLane(context.player().uuid()).ifPresent(lane -> {
            long payout = lane.towers().stream()
                    .filter(tower -> tower.health() > 0.0)
                    .filter(PlantCombatTower.class::isInstance)
                    .map(PlantCombatTower.class::cast)
                    .filter(tower -> context.player().uuid().equals(tower.ownerPlayer()))
                    .mapToLong(PlantCombatTower::diamondPerWave)
                    .sum();
            if (payout > 0L) {
                context.player().economy().addMineral(payout);
            }
        });
    }

    @Override
    public void onEliminated(JobContext context) {
        PlantSoilStates.clear(context.player().uuid());
    }
}
