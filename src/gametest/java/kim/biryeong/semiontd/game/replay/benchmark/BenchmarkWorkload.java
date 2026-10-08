package kim.biryeong.semiontd.game.replay.benchmark;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.replay.ReplayCapture;
import net.minecraft.world.entity.Entity;

public final class BenchmarkWorkload {
    private static final Map<ReplayCapture, BenchmarkWorkload> CAPTURES = new IdentityHashMap<>();
    private static final Map<ReplayCapture, Integer> ID_BASES = new IdentityHashMap<>();
    private final Set<Entity> dead = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Monster> rewarded = Collections.newSetFromMap(new IdentityHashMap<>());
    private long attacks;
    private long damageEvents;
    private long spawns;
    private long deaths;
    private long rewards;
    private long shots;
    private long presses;
    private double damage;
    private boolean active;

    public void attach(ReplayCapture capture, int teamOrdinal, boolean countWork) {
        if (countWork) { CAPTURES.put(capture, this); }
        ID_BASES.put(capture, (teamOrdinal + 1) * 1000000);
    }

    public void detach(ReplayCapture capture) {
        CAPTURES.remove(capture);
        ID_BASES.remove(capture);
    }

    public static void identity(ReplayCapture capture, Entity entity) {
        Integer base = ID_BASES.get(capture);
        if (base != null && entity instanceof net.minecraft.world.entity.LivingEntity) {
            int id = base + entity.getId() % 1000000;
            if (((net.minecraft.server.level.ServerLevel) entity.level()).getEntity(id) != null) {
                throw new IllegalStateException("Benchmark global team identity is already occupied");
            }
            entity.setId(id);
        }
    }

    public void start() {
        active = true;
    }

    public void stop() {
        active = false;
    }

    public static void event(ReplayCapture capture, String kind, Object target, double amount, boolean accepted) {
        BenchmarkWorkload workload = CAPTURES.get(capture);
        if (workload == null || !workload.active) {
            return;
        }
        switch (kind) {
            case "attack" -> workload.attacks++;
            case "damage" -> {
                workload.damageEvents++;
                workload.damage += amount;
            }
            case "spawn" -> {
                if (accepted && target instanceof SemionMonsterEntity) {
                    workload.spawns++;
                }
            }
            case "death" -> {
                if (target instanceof SemionMonsterEntity && workload.dead.add((Entity) target)) {
                    workload.deaths++;
                }
            }
            case "reward" -> {
                if (target instanceof Monster monster && monster.rewardGranted() && workload.rewarded.add(monster)) {
                    workload.rewards++;
                }
            }
            case "shot" -> workload.shots++;
            case "press" -> {
                if (accepted) {
                    workload.presses++;
                }
            }
            default -> throw new IllegalArgumentException("Unknown benchmark event " + kind);
        }
    }

    public JsonObject snapshot() {
        JsonObject result = new JsonObject();
        result.addProperty("attacks", attacks);
        result.addProperty("damage_events", damageEvents);
        result.addProperty("damage_dealt", damage);
        result.addProperty("natural_spawns", spawns);
        result.addProperty("monster_deaths", deaths);
        result.addProperty("kill_reward_events", rewards);
        result.addProperty("instant_dispenser_shots", shots);
        result.addProperty("plate_presses", presses);
        return result;
    }
}
