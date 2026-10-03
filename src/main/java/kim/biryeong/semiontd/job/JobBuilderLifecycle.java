package kim.biryeong.semiontd.job;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.map.TeamArena;
import net.minecraft.server.level.ServerLevel;
import kim.biryeong.semiontd.tower.demonlord.DemonLordService;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService;
import net.minecraft.server.level.ServerPlayer;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.tower.adversary.AdversaryProgressStates;
import kim.biryeong.semiontd.tower.adversary.AdversaryTeamEffects;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityStates;
import kim.biryeong.semiontd.tower.army.ArmyStates;
import kim.biryeong.semiontd.tower.atlantis.AtlantisPressure;
import kim.biryeong.semiontd.tower.atlantis.AtlantisStates;
import kim.biryeong.semiontd.tower.engineer.EngineerPressStates;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyStates;
import kim.biryeong.semiontd.tower.hero.HeroPartyStates;
import kim.biryeong.semiontd.tower.mage.MageStates;
import kim.biryeong.semiontd.tower.queen.QueenStates;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import net.minecraft.resources.Identifier;
import kim.biryeong.semiontd.summon.SummonMonsterType;

public final class JobBuilderLifecycle {
    private static final Map<Identifier, JobLifecycle> LIFECYCLES = Map.ofEntries(
            Map.entry(DefaultJob.ID, JobLifecycle.NONE),
            Map.entry(AnimalTowerJob.ID, JobLifecycle.NONE),
            Map.entry(BodyTowerJob.ID, JobLifecycle.NONE),
            Map.entry(EndTowerJob.ID, JobLifecycle.NONE),
            Map.entry(LegionTowerJob.ID, JobLifecycle.NONE),
            Map.entry(NetherTowerJob.ID, JobLifecycle.NONE),
            Map.entry(OceanTowerJob.ID, JobLifecycle.NONE),
            Map.entry(PetTowerJob.ID, JobLifecycle.NONE),
            Map.entry(ResonanceTowerJob.ID, JobLifecycle.NONE),
            Map.entry(UndeadTowerJob.ID, JobLifecycle.NONE),
            Map.entry(VillagerTowerJob.ID, JobLifecycle.NONE),
            Map.entry(AdversaryTowerJob.ID, new JobAdversaryLifecycle()),
            Map.entry(AncientCityTowerJob.ID, new JobAncientCityLifecycle()),
            Map.entry(ArmyTowerJob.ID, new JobArmyLifecycle()),
            Map.entry(AtlantisTowerJob.ID, new JobAtlantisLifecycle()),
            Map.entry(BlueprintTowerJob.ID, new JobBlueprintLifecycle()),
            Map.entry(DemonLordTowerJob.ID, new JobDemonLordLifecycle()),
            Map.entry(DeveloperTowerJob.ID, new JobDeveloperLifecycle()),
            Map.entry(EngineerTowerJob.ID, new JobEngineerLifecycle()),
            Map.entry(FrostTowerJob.ID, new JobFrostLifecycle()),
            Map.entry(FutureAgencyTowerJob.ID, new JobFutureAgencyLifecycle()),
            Map.entry(GambleTowerJob.ID, new JobGambleLifecycle()),
            Map.entry(HeroPartyTowerJob.ID, new JobHeroPartyLifecycle()),
            Map.entry(IllagerTowerJob.ID, new JobIllagerLifecycle()),
            Map.entry(InsectTowerJob.ID, new JobInsectLifecycle()),
            Map.entry(MageTowerJob.ID, new JobMageLifecycle()),
            Map.entry(PirateTowerJob.ID, new JobPirateLifecycle()),
            Map.entry(PlantTowerJob.ID, new JobPlantLifecycle()),
            Map.entry(QueenTowerJob.ID, new JobQueenLifecycle()),
            Map.entry(SuccubusTowerJob.ID, new JobSuccubusLifecycle()),
            Map.entry(ThunderTowerJob.ID, new JobThunderLifecycle()),
            Map.entry(VillagerAdvTowerJob.ID, new JobVillagerAdvLifecycle()),
            Map.entry(WarlockTowerJob.ID, new JobWarlockLifecycle())
    );

    static java.util.Set<Identifier> registeredJobIds() {
        return LIFECYCLES.keySet();
    }

    private JobBuilderLifecycle() {
    }

    static void onSelected(Identifier jobId, JobContext context) {
        LIFECYCLES.getOrDefault(jobId, JobLifecycle.NONE).onSelected(context);
    }

    static void onMatchStarted(Identifier jobId, JobContext context) {
        LIFECYCLES.getOrDefault(jobId, JobLifecycle.NONE).onMatchStarted(context);
    }

    static void onRoundStarted(Identifier jobId, JobContext context, int round) {
        LIFECYCLES.getOrDefault(jobId, JobLifecycle.NONE).onRoundStarted(context, round);
    }

    static void onRoundEnded(Identifier jobId, JobContext context, int round) {
        LIFECYCLES.getOrDefault(jobId, JobLifecycle.NONE).onRoundEnded(context, round);
    }

    static void onEliminated(Identifier jobId, JobContext context) {
        LIFECYCLES.getOrDefault(jobId, JobLifecycle.NONE).onEliminated(context);
    }

    static void onMatchClosed(Identifier jobId, JobContext context) {
        LIFECYCLES.getOrDefault(jobId, JobLifecycle.NONE).onMatchClosed(context);
    }

    static void onSummonedMonster(Identifier jobId, JobContext context, SummonMonsterType summonType, Monster monster) {
        LIFECYCLES.getOrDefault(jobId, JobLifecycle.NONE).onSummonedMonster(context, summonType, monster);
    }

    static void onMonsterKilled(Identifier jobId, JobContext context, Monster monster, long mineralReward) {
        LIFECYCLES.getOrDefault(jobId, JobLifecycle.NONE).onMonsterKilled(context, monster, mineralReward);
    }

    public static void onPlayerEliminated(ServerPlayer player) {
        DemonLordService.cleanupPlayer(player);
    }

    public static void onPlayerDisconnected(ServerPlayer player) {
        kim.biryeong.semiontd.ui.GambleRevealService.clear(player.getUUID());
        DemonLordService.cleanupPlayer(player);
        FrostFullOperationService.cleanupPlayer(player);
    }

    public static void onWaveStarted(SemionGame game, int round) {
        VillagerAdvStates.onWaveStarted(game, round);
    }

    public static void onWaveCleared(SemionGame game, int round) {
        VillagerAdvStates.onWaveCleared(game, round);
    }

    public static void closePlayerRuntime(SemionGame game) {
        for (SemionPlayer semionPlayer : game.players().values()) {
            ServerPlayer online = game.arena().teamArena(semionPlayer.teamId())
                    .map(TeamArena::world)
                    .map(ServerLevel::getServer)
                    .map(server -> server.getPlayerList().getPlayer(semionPlayer.uuid()))
                    .orElse(null);
            if (online == null) {
                DemonLordService.clearPlayerState(semionPlayer.uuid());
            } else {
                DemonLordService.cleanupPlayer(online);
            }
        }
    }

    public static void closeBeforeLanes(Collection<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            VillagerAdvStates.clear(playerId);
            AncientCityStates.clear(playerId);
            EngineerPressStates.clear(playerId);
        }
    }

    public static void closeAfterLanes(Collection<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            AtlantisStates.clear(playerId);
            AtlantisPressure.clearPlayer(playerId);
        }
        // Rival tower removal reconciles its installed-score ledger while lanes close,
        // so clear Adversary state after every tower has been detached.
        for (UUID playerId : playerIds) {
            AdversaryProgressStates.clear(playerId);
            AdversaryTeamEffects.unregisterPlayer(playerId);
            MageStates.clear(playerId);
            FutureAgencyStates.clear(playerId);
            QueenStates.clear(playerId);
            HeroPartyStates.clear(playerId);
            ArmyStates.clear(playerId);
        }
    }
}
