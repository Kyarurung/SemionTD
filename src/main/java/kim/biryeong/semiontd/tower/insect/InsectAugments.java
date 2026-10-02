package kim.biryeong.semiontd.tower.insect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.Tower;

public final class InsectAugments {
    static final String SHELL = "job_insect_towers_s";
    static final String HATCH = "job_insect_towers_g1";
    static final String MARCH = "job_insect_towers_g2";
    static final String COLONY = "job_insect_towers_p";
    static final String EVOLUTION_I = "job_insect_towers_s2";
    static final String EVOLUTION_II = "job_insect_towers_g3";
    private static final Map<UUID, Round> ROUNDS = new HashMap<>();

    private InsectAugments() {}

    public static double evolutionBonus(Tower tower, String parameter) {
        if (!(tower instanceof InsectUnitTower) && !(tower instanceof InsectSpawnerTower)) return 0;
        double bonus = 0;
        for (String card : List.of(EVOLUTION_I, EVOLUTION_II)) {
            if (tower.augmentSnapshot().has(card)) bonus += tower.augmentSnapshot().parameter(card, parameter, 0);
        }
        return bonus;
    }

    static void beginWave(UUID owner, int currentRound) {
        Round round = ROUNDS.get(owner);
        if (round == null || round.number != currentRound) {
            round = new Round();
            round.number = currentRound;
            ROUNDS.put(owner, round);
        }
    }

    static void reserveLarvae(InsectUnitTower source, PlayerLane lane) {
        if (lane == null || source.isLarva() || !AugmentCombat.allowsTriggers()
                || !source.augmentSnapshot().has(MARCH)) return;
        Round round = ROUNDS.computeIfAbsent(source.ownerPlayer(), ignored -> new Round());
        int count = Math.min((int) source.augmentSnapshot().parameter(MARCH, "spawnCount", 2),
                Math.max(0, (int) source.augmentSnapshot().parameter(MARCH, "activeCap", 8) - activeLarvae(lane)));
        if (count <= 0) return;
        double ratio = source.augmentSnapshot().parameter(MARCH, "statRatio", .4);
        double attack = source.runtimeEntity(lane).map(entity -> entity.attackDamageAmount(null)).orElse(source.type().damage());
        TowerType old = source.type();
        TowerType type = new TowerType(old.id(), old.displayName() + " 유충", old.category(), 0,
                Math.max(1, source.currentMaxHealth() * ratio), old.range(), attack * ratio,
                old.attackIntervalTicks(), old.aggroPriority(), old.description(), old.visual(), List.of(), old.primaryDamageType());
        for (int index = 0; index < count; index++) {
            InsectUnitTower larva = new InsectUnitTower(type, source.ownerPlayer(), source.teamId(), source.laneId(),
                    source.position(), source.position());
            larva.markLarva(source);
            round.pending.add(new Pending(lane, source, larva));
        }
    }

    public static void flush(PlayerLane lane) {
        Round round = ROUNDS.get(lane.ownerPlayer());
        if (round == null || round.pending.isEmpty()) return;
        List<Pending> pending = round.pending.stream().filter(spawn -> spawn.lane == lane).toList();
        round.pending.removeAll(pending);
        for (Pending spawn : pending) {
            if (spawn.lane == lane && lane.towers().contains(spawn.source)) {
                lane.addTower(spawn.larva);
            }
        }
    }

    public static boolean hasPendingDefender(PlayerLane lane) {
        Round round = ROUNDS.get(lane.ownerPlayer());
        return round != null && round.pending.stream()
                .anyMatch(spawn -> spawn.lane == lane && lane.towers().contains(spawn.source));
    }

    public static void clear(UUID owner) {
        ROUNDS.remove(owner);
    }

    static int activeLarvae(PlayerLane lane) {
        if (lane == null) return 0;
        int living = (int) lane.towers().stream().filter(tower -> tower instanceof InsectUnitTower unit
                && unit.isLarva() && unit.ownerPlayer().equals(lane.ownerPlayer())
                && unit.runtimeEntity(lane).map(entity -> entity.isAlive()).orElse(unit.health() > 0)).count();
        Round round = ROUNDS.get(lane.ownerPlayer());
        return living + (round == null ? 0 : (int) round.pending.stream()
                .filter(spawn -> spawn.lane == lane && lane.towers().contains(spawn.source)).count());
    }

    private static final class Round {
        private int number;
        private final List<Pending> pending = new ArrayList<>();
    }

    private record Pending(PlayerLane lane, InsectUnitTower source, InsectUnitTower larva) {}
}
