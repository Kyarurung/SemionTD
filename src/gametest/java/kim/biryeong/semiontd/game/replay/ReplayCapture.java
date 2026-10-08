package kim.biryeong.semiontd.game.replay;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class ReplayCapture implements AutoCloseable {
    private static final Map<ServerLevel, ReplayCapture> ACTIVE = new IdentityHashMap<>();
    private static final String[] CHANNELS = {"attacks", "damage", "deaths", "spawns", "rewards", "projectiles", "circuits"};
    private final ServerLevel world;
    private final PlayerLane[] lane = new PlayerLane[1];
    private final JsonObject metadata;
    private final JsonArray samples = new JsonArray();
    private final Map<Entity, String> ids = new IdentityHashMap<>();
    private final Map<String, LivingEntity> actors = new LinkedHashMap<>();
    private final Map<Tower, String> components = new LinkedHashMap<>();
    private final Map<String, JsonArray> events = new LinkedHashMap<>();
    private final Map<String, Long> totals = new LinkedHashMap<>();
    private final Map<Monster, Long> rewardBefore = new IdentityHashMap<>();
    private final Map<String, Boolean> dead = new LinkedHashMap<>();
    private int waveSpawn;
    private int ambientSpawn;
    private int placementSequence = -1;
    private int placementSpawn;
    private int platePresses;
    private boolean recording;

    public ReplayCapture(ServerLevel world, JsonObject metadata) {
        this.world = world;
        this.metadata = metadata;
        for (String channel : CHANNELS) {
            events.put(channel, new JsonArray());
            totals.put(channel, 0L);
        }
        if (ACTIVE.putIfAbsent(world, this) != null) {
            throw new IllegalStateException("A world already has a replay collector");
        }
    }

    public static ReplayCapture current(Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel level ? ACTIVE.get(level) : null;
    }

    public static ReplayCapture current(ServerLevel world) {
        return ACTIVE.get(world);
    }

    public static ReplayCapture current(Monster monster) {
        return ACTIVE.values().stream().filter(capture -> capture.actors.values().stream().anyMatch(actor ->
                actor instanceof SemionMonsterEntity entity && entity.runtimeMonster() == monster)).findFirst().orElse(null);
    }

    public void component(Tower tower, int sequence) {
        components.put(tower, "p02/component/" + sequence);
    }

    public void bind(PlayerLane lane) {
        this.lane[0] = lane;
    }

    public void placement(int sequence, Runnable operation) {
        placementSequence = sequence;
        placementSpawn = 0;
        try {
            operation.run();
        } finally {
            placementSequence = -1;
        }
    }

    public void spawn(Entity entity, boolean accepted) {
        if (!(entity instanceof LivingEntity living) || !accepted || ids.containsKey(entity)) {
            return;
        }
        String id;
        long seed;
        if (placementSequence >= 0) {
            id = "p02/placement/" + placementSequence + "/" + placementSpawn++;
            seed = 1L + 104729L * (placementSequence + 1) + placementSpawn;
        } else if (entity instanceof SemionMonsterEntity) {
            id = "wave/" + waveSpawn++;
            seed = 1000001L + waveSpawn;
        } else {
            id = "ambient/" + ambientSpawn++;
            seed = 2000001L + ambientSpawn;
        }
        living.getRandom().setSeed(seed);
        ids.put(entity, id);
        actors.put(id, living);
        JsonObject event = event("SPAWN", id);
        event.addProperty("type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        event.addProperty("rng_seed", Long.toString(seed));
        add("spawns", event);
    }

    public String id(Entity entity) {
        if (entity == null) {
            return null;
        }
        String value = ids.get(entity);
        if (value == null) {
            throw new IllegalStateException("Unobserved actor in target world: " + entity.getType());
        }
        return value;
    }

    public void attack(Entity source, Entity target, String kind, double amount) {
        JsonObject event = event(kind, id(source));
        event.addProperty("target", id(target));
        event.addProperty("amount", amount);
        add("attacks", event);
    }

    public void damage(Entity source, LivingEntity target, String kind, double requested, double dealt, boolean killed) {
        JsonObject event = event(kind, id(target));
        event.addProperty("source", source == null ? null : id(source));
        event.addProperty("requested", requested);
        event.addProperty("dealt", dealt);
        event.addProperty("killed", killed);
        add("damage", event);
        if (killed) {
            death(target);
        }
    }

    public void death(Entity entity) {
        String id = id(entity);
        if (dead.putIfAbsent(id, true) == null) {
            add("deaths", event("DEATH", id));
        }
    }

    public void projectile(Entity source, Entity target, double damage, boolean pierce) {
        attack(source, target, "DISPENSER_ATTACK", damage);
        JsonObject event = event("INSTANT_DISPENSER_SHOT", id(source));
        event.addProperty("target", id(target));
        event.addProperty("damage", damage);
        event.addProperty("pierce", pierce);
        add("projectiles", event);
    }

    public void plate(EngineerCircuitTower plate, boolean accepted) {
        if (!accepted || lane[0] == null) {
            return;
        }
        JsonObject event = event("PLATE_PRESS", components.get(plate));
        if (recording) {
            platePresses++;
        }
        event.add("position", vector(Vec3.atLowerCornerOf(plate.circuitPosition())));
        add("circuits", event);
    }

    public void beforeReward(Monster monster, Map<UUID, SemionPlayer> players) {
        if (monster.lastHitPlayerId().isPresent() && !monster.rewardGranted()) {
            SemionPlayer player = players.get(monster.lastHitPlayerId().get());
            if (player != null && lane[0] != null && player.uuid().equals(lane[0].ownerPlayer())) {
                rewardBefore.put(monster, player.economy().diamond());
            }
        }
    }

    public void afterReward(Monster monster, Map<UUID, SemionPlayer> players) {
        Long before = rewardBefore.remove(monster);
        if (before == null || !monster.rewardGranted()) {
            return;
        }
        SemionPlayer player = players.get(monster.lastHitPlayerId().orElseThrow());
        Entity entity = actors.values().stream().filter(value -> value instanceof SemionMonsterEntity actor
                && actor.runtimeMonster() == monster).findFirst().orElseThrow();
        JsonObject event = event("KILL_REWARD", id(entity));
        event.addProperty("player", "p02");
        event.addProperty("diamond", Long.toString(player.economy().diamond() - before));
        add("rewards", event);
    }

    public void start() {
        recording = true;
        events.values().forEach(array -> {while (!array.isEmpty()) {array.remove(array.size() - 1);}});
        sample(0);
    }

    public void sample(long tick) {
        if (tick != samples.size()) {
            throw new IllegalStateException("Expected continuous replay tick " + samples.size() + " but got " + tick);
        }
        JsonObject sample = new JsonObject();
        sample.addProperty("tick", tick);
        JsonObject states = new JsonObject();
        for (var entry : components.entrySet()) {
            Tower tower = entry.getKey();
            JsonObject state = new JsonObject();
            state.addProperty("health", tower.health());
            state.add("position", vector(new Vec3(tower.position().x(), tower.position().y(), tower.position().z())));
            state.addProperty("target", (String) null);
            state.addProperty("alive", tower.health() > 0);
            int cooldown = tower instanceof kim.biryeong.semiontd.tower.engineer.EngineerTrapTower
                    ? ((Number) field(tower, "actionCooldown")).intValue() : 0;
            state.addProperty("cooldown", cooldown);
            if (tower instanceof kim.biryeong.semiontd.tower.engineer.EngineerTrapTower trap) {
                state.addProperty("active_ticks", trap.activeTicksRemaining());
                state.addProperty("armed", trap.armed());
            }
            states.add(entry.getValue(), state);
        }
        for (var entry : actors.entrySet()) {
            LivingEntity actor = entry.getValue();
            JsonObject state = new JsonObject();
            double health = actor instanceof SemionMonsterEntity monster && monster.runtimeMonster() != null
                    ? monster.runtimeMonster().health()
                    : actor instanceof SemionTowerEntity tower && tower.runtimeTower() != null
                    ? tower.runtimeTower().health() : actor.getHealth();
            state.addProperty("health", health);
            state.add("position", vector(actor.position()));
            Entity target = actor instanceof SemionTowerEntity tower ? tower.currentAttackTarget()
                    : actor instanceof Mob mob ? mob.getTarget() : null;
            state.addProperty("target", target == null ? null : id(target));
            state.addProperty("cooldown", cooldown(actor));
            state.addProperty("alive", health > 0 && !actor.isRemoved());
            states.add(entry.getKey(), state);
        }
        sample.add("actors", states);
        if (lane[0] != null) {
            for (var tower : lane[0].towers()) {
                if (tower instanceof EngineerCircuitTower circuit) {
                    JsonObject signal = event("AUTHORITATIVE_PLATE_STATE", components.get(tower));
                    signal.add("position", vector(Vec3.atLowerCornerOf(circuit.circuitPosition())));
                    signal.addProperty("pressed", circuit.platePressed(lane[0]));
                    events.get("circuits").add(signal);
                }
            }
        }
        for (String channel : CHANNELS) {
            JsonArray values = events.get(channel);
            sample.add(channel, values.deepCopy());
            totals.merge(channel, (long) values.size(), Long::sum);
            while (!values.isEmpty()) {
                values.remove(values.size() - 1);
            }
        }
        samples.add(sample);
    }

    private static int cooldown(LivingEntity actor) {
        if (!(actor instanceof Mob mob)) {
            return 0;
        }
        Object selector = field(mob, "goalSelector");
        try {
            var goals = (java.util.Set<?>) selector.getClass().getMethod("getAvailableGoals").invoke(selector);
            for (Object wrapped : goals) {
                Object goal = wrapped.getClass().getMethod("getGoal").invoke(wrapped);
                if (!goal.getClass().getSimpleName().equals("TowerAttackMonsterGoal")
                        && !goal.getClass().getSimpleName().equals("MonsterAttackTargetGoal")) {
                    continue;
                }
                try {
                    return Math.max(1, ((Number) field(goal, "cooldownTicks")).intValue());
                } catch (IllegalStateException legacyFieldAbsent) {
                    double remaining = ((Number) field(field(goal, "cooldown"), "remaining")).doubleValue();
                    if (remaining != Math.rint(remaining)) {
                        throw new IllegalStateException("Fractional cooldown cannot be represented by this 40 TPS trace");
                    }
                    return Math.max(1, (int) remaining);
                }
            }
            return 0;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot observe actual goal cooldown", error);
        }
    }

    public static Object field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException absent) {
                continue;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
        throw new IllegalStateException("Missing trace field " + target.getClass().getName() + "." + name);
    }

    public void write(Path output) throws java.io.IOException {
        if (platePresses == 0 || waveSpawn != 12) {
            throw new IllegalStateException("The controlled opening must exercise its plates and all twelve natural spawns");
        }
        for (String channel : CHANNELS) {
            if (totals.get(channel) == 0L) {
                throw new IllegalStateException("Capture has no exercised " + channel + " coverage");
            }
        }
        JsonObject trace = new JsonObject();
        trace.add("metadata", metadata);
        trace.add("samples", samples);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(trace) + "\n", StandardCharsets.UTF_8);
    }

    private void add(String channel, JsonObject event) {
        if (recording) {
            events.get(channel).add(event);
        }
    }

    private static JsonObject event(String kind, String actor) {
        JsonObject event = new JsonObject();
        event.addProperty("kind", kind);
        event.addProperty("actor", actor);
        return event;
    }

    private static JsonArray vector(Vec3 position) {
        JsonArray vector = new JsonArray();
        vector.add(position.x);
        vector.add(position.y);
        vector.add(position.z);
        return vector;
    }

    @Override
    public void close() {
        ACTIVE.remove(world, this);
    }
}
