package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.TowerRoundMetricsTracker;
import kim.biryeong.semiontd.tower.adversary.AdversaryAugments;
import kim.biryeong.semiontd.tower.army.ArmyStates;
import kim.biryeong.semiontd.tower.demonlord.DemonLordService;
import kim.biryeong.semiontd.tower.frost.FrostAugments;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyAgentTower;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.insect.InsectAugments;
import kim.biryeong.semiontd.tower.legion.LegionAugments;
import kim.biryeong.semiontd.tower.pirate.PirateAugments;
import kim.biryeong.semiontd.tower.resonance.ResonanceService;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import kim.biryeong.semiontd.tower.undead.UndeadAugments;
import kim.biryeong.semiontd.tower.villager.VillagerAdvAugments;

public final class JobLaneLifecycle {
    private JobLaneLifecycle() {
    }

    public static void beforeRoundReset(PlayerLane lane) {
        kim.biryeong.semiontd.tower.magicschool.MagicSchoolTransfiguration.clear(lane.ownerPlayer());
        for (var monster : lane.activeMonsters()) {
            if (lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof kim.biryeong.semiontd.entity.monster.SemionMonsterEntity entity) {
                entity.schoolSpells().clearRound();
            }
        }
        LegionAugments.clear(lane);
        UndeadAugments.resetRound(lane);
        VillagerAdvAugments.resetWave(lane);
        FrostAugments.endWave(lane);
        PirateAugments.endWave(lane);
    }

    public static void afterTemporaryCopiesRemoved(PlayerLane lane) {
        FrostFullOperationService.endWave(lane);
        // markWaveStarted 의 짝: 여기서 전투를 풀지 않으면 준비 단계까지 스킬 핫바가 유지됩니다.
        DemonLordService.endRound(lane.ownerPlayer());
        lane.clearTranscendence();
        SuccubusDreams.clearLane(lane);
    }

    public static void prepareWave(PlayerLane lane, int currentRound) {
        AugmentCombat.startWave(lane, currentRound);
        FrostFullOperationService.beginWave(lane);
        lane.clearTranscendence();
    }

    public static void beforeTowerWaveStarted(PlayerLane lane) {
        LegionAugments.onWaveStarted(lane);
        VillagerAdvAugments.startWave(lane);
        IllagerRaidStates.onWaveStarted(lane);
        FrostAugments.beginWave(lane);
    }

    public static TowerRoundMetricsTracker afterTowerWaveStarted(PlayerLane lane, int currentRound) {
        kim.biryeong.semiontd.tower.magicschool.MagicSchoolDeathEaters.onWaveStarted(lane, currentRound);
        ResonanceService.captureWaveStart(lane);
        AugmentCombat.captureWaveStartHealth(lane);
        AdversaryAugments.captureWaveStart(lane);
        FutureAgencyAgentTower.captureWaveStart(lane);
        PirateAugments.beginWave(lane);
        ArmyStates.spawnReserves(lane, currentRound);
        // 마왕은 여기서 전투 상태가 됩니다. 라운드 시작(준비 단계)에 걸면 상점을 열 수 없는
        // 채로 준비 시간을 보내게 되고, 스스로 물러난 뒤 웨이브가 시작돼도 복귀하지 못합니다.
        DemonLordService.beginWave(lane, currentRound);
        return DemonLordService.roundMetricsTracker(lane.ownerPlayer());
    }

    public static void beforeTowersCleared(PlayerLane lane) {
        kim.biryeong.semiontd.tower.magicschool.MagicSchoolTransfiguration.clear(lane.ownerPlayer());
        LegionAugments.clear(lane);
        UndeadAugments.resetRound(lane);
        FrostAugments.endWave(lane);
        PirateAugments.endWave(lane);
        InsectAugments.clear(lane.ownerPlayer());
    }
}
