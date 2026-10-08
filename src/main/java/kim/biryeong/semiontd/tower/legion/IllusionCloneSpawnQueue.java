package kim.biryeong.semiontd.tower.legion;

import java.util.List;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.world.phys.Vec3;

public final class IllusionCloneSpawnQueue {
    private static final IllusionSpawnSchedule<PendingCloneSpawn> PENDING_CLONE_SPAWNS = new IllusionSpawnSchedule<>();

    public static void enqueue(
            IllusionSummonerTower owner,
            PlayerLane lane,
            Tower sourceTower,
            IllusionProfile profile,
            List<Vec3> offsets
    ) {
        if (owner == null || lane == null || sourceTower == null || profile.cloneCount() <= 0
                || sourceTower.health() <= 0.0 || offsets == null || offsets.isEmpty()) {
            return;
        }
        int spreadTicks = TowerBalanceRuntime.illusionCloneSpawnSpreadTicks();
        for (int index = 0; index < profile.cloneCount(); index++) {
            Vec3 offset = offsets.get(index % offsets.size());
            int delayTicks = (int) Math.floor(index * (double) spreadTicks / profile.cloneCount());
            PENDING_CLONE_SPAWNS.enqueue(new PendingCloneSpawn(owner, lane, sourceTower, profile, offset),
                    delayTicks + (delayTicks > 0 ? 1 : 0));
        }
    }

    public static void tick() {
        PENDING_CLONE_SPAWNS.tick(TowerBalanceRuntime.illusionCloneMaxSpawnsPerTick(),
                IllusionCloneSpawnQueue::spawn);
    }

    public static void tick(GameArena arena) {
        if (arena == null) {
            return;
        }
        PENDING_CLONE_SPAWNS.tick(pending -> arena.containsWorld(pending.lane().arenaWorld()),
                TowerBalanceRuntime.illusionCloneMaxSpawnsPerTick(), IllusionCloneSpawnQueue::spawn);
    }

    private static boolean spawn(PendingCloneSpawn pending) {
        if (!pending.isValid()) {
            return false;
        }
        if (pending.child != null) {
            pending.owner().spawnQueuedChild(pending.lane(), pending.sourceTower(), pending.child, pending.offset());
        } else {
            pending.owner().spawnQueuedClone(pending.lane(), pending.sourceTower(), pending.profile(), pending.offset());
        }
        return true;
    }

    public static void cancel(IllusionSummonerTower owner) {
        if (owner != null) {
            PENDING_CLONE_SPAWNS.removeIf(pending -> pending.owner() == owner && pending.child == null);
        }
    }

    static void enqueueChild(IllusionSummonerTower owner, PlayerLane lane, Tower parent,
                             LegionAugments.Material material, Vec3 position, LegionAugments.Wave wave) {
        PendingCloneSpawn spawn = new PendingCloneSpawn(owner, lane, parent, null, position);
        spawn.child = material;
        spawn.wave = wave;
        PENDING_CLONE_SPAWNS.enqueue(spawn, 1);
    }

    static void cancelAugmentChildren(PlayerLane lane, java.util.UUID original) {
        PENDING_CLONE_SPAWNS.removeIf(spawn -> spawn.child != null && spawn.lane == lane
                && (original == null || original.equals(spawn.child.original)));
    }

    public static void clear() {
        PENDING_CLONE_SPAWNS.clear();
    }

    private IllusionCloneSpawnQueue() throws IllegalAccessException {
        throw new IllegalAccessException("Utility Class");
    }

    private static final class PendingCloneSpawn {
        private final IllusionSummonerTower owner;
        private final PlayerLane lane;
        private final Tower sourceTower;
        private final IllusionProfile profile;
        private final Vec3 offset;
        private LegionAugments.Material child;
        private LegionAugments.Wave wave;

        private PendingCloneSpawn(
                IllusionSummonerTower owner,
                PlayerLane lane,
                Tower sourceTower,
                IllusionProfile profile,
                Vec3 offset
        ) {
            this.owner = owner;
            this.lane = lane;
            this.sourceTower = sourceTower;
            this.profile = profile;
            this.offset = offset;
        }

        private IllusionSummonerTower owner() {
            return owner;
        }

        private PlayerLane lane() {
            return lane;
        }

        private Tower sourceTower() {
            return sourceTower;
        }

        private IllusionProfile profile() {
            return profile;
        }

        private Vec3 offset() {
            return offset;
        }

        private boolean isValid() {
            if (child != null) return LegionAugments.validChild(lane, wave, child);
            return lane.towers().contains(owner)
                    && sourceTower.health() > 0.0
                    && (sourceTower == owner || lane.towers().contains(sourceTower));
        }
    }
}
