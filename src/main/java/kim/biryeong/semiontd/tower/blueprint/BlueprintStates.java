package kim.biryeong.semiontd.tower.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * 플레이어별 설계도. 한 경기 동안만 살고, 경기 시작·탈락·경기 종료 때 지웁니다.
 *
 * <p>설계도마다 카탈로그에 1단계 시작 타워로 올려 두어서 설치·판매·타워 수·증강 같은 기존 흐름을 그대로 탑니다.
 * 카탈로그는 설정을 다시 읽을 때 통째로 새로 지어지므로 그 뒤 {@link #reinstall()}로 다시 올립니다. 카탈로그와
 * 같은 잠금을 써서 둘의 상태가 어긋나지 않게 합니다.
 */
public final class BlueprintStates {
    private static final Object LOCK = ProductionTowerCatalog.class;
    private static final Map<UUID, List<Blueprint>> BY_OWNER = new LinkedHashMap<>();
    private static final Map<String, Blueprint> BY_TOWER_ID = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_NUMBER = new HashMap<>();
    private static final int MAX_NAME_LENGTH = 16;

    private BlueprintStates() {
    }

    public enum Result {
        SUCCESS,
        INVALID_NAME,
        INVALID_STATS,
        UNKNOWN_VISUAL,
        TOO_MANY
    }

    /** @param message 실패 이유(성공이면 빈 문자열) */
    public record Creation(Result result, Blueprint blueprint, String message) {
        public boolean success() {
            return result == Result.SUCCESS;
        }

        static Creation failure(Result result, String message) {
            return new Creation(result, null, message);
        }
    }

    /** 설계도를 만들고 카탈로그에 올립니다. 만드는 데 비용은 들지 않습니다(설치할 때 가격을 냅니다). */
    public static Creation create(UUID owner, String rawName, BlueprintStats stats, String visualSourceId) {
        String name = sanitizeName(rawName);
        if (name.isEmpty()) {
            return Creation.failure(Result.INVALID_NAME, "이름은 1~" + MAX_NAME_LENGTH + "자여야 합니다.");
        }
        Optional<String> invalid = BlueprintPricing.validate(stats);
        if (invalid.isPresent()) {
            return Creation.failure(Result.INVALID_STATS, invalid.get());
        }
        Optional<BlueprintVisuals.Option> visual = BlueprintVisuals.find(visualSourceId);
        if (visual.isEmpty()) {
            return Creation.failure(Result.UNKNOWN_VISUAL, "고를 수 없는 겉모습입니다: " + visualSourceId);
        }
        synchronized (LOCK) {
            List<Blueprint> owned = BY_OWNER.computeIfAbsent(owner, ignored -> new ArrayList<>());
            if (owned.size() >= BlueprintPricing.maxBlueprints()) {
                return Creation.failure(Result.TOO_MANY, "설계도는 한 경기에 " + BlueprintPricing.maxBlueprints() + "장까지 만들 수 있습니다.");
            }
            int number = NEXT_NUMBER.merge(owner, 1, Integer::sum);
            Blueprint blueprint = new Blueprint(
                    towerId(owner, number), owner, name, stats,
                    visual.get().sourceTowerId(), visual.get().visual(), BlueprintPricing.price(stats)
            );
            owned.add(blueprint);
            BY_TOWER_ID.put(blueprint.towerId(), blueprint);
            register(blueprint);
            return new Creation(Result.SUCCESS, blueprint, "");
        }
    }

    public static List<Blueprint> of(UUID owner) {
        synchronized (LOCK) {
            return List.copyOf(BY_OWNER.getOrDefault(owner, List.of()));
        }
    }

    public static Optional<Blueprint> find(String towerId) {
        synchronized (LOCK) {
            return Optional.ofNullable(BY_TOWER_ID.get(towerId));
        }
    }

    public static Optional<Blueprint> find(TowerType type) {
        return type == null ? Optional.empty() : find(type.id());
    }

    /** 이 플레이어의 설계도를 모두 지우고 카탈로그에서도 내립니다. */
    public static void clear(UUID owner) {
        synchronized (LOCK) {
            List<Blueprint> owned = BY_OWNER.remove(owner);
            NEXT_NUMBER.remove(owner);
            if (owned == null) {
                return;
            }
            for (Blueprint blueprint : owned) {
                BY_TOWER_ID.remove(blueprint.towerId());
                ProductionTowerCatalog.unregister(blueprint.towerId());
            }
        }
    }

    public static void clearAll() {
        synchronized (LOCK) {
            for (UUID owner : List.copyOf(BY_OWNER.keySet())) {
                clear(owner);
            }
        }
    }

    /** 카탈로그가 다시 지어진 뒤 빠진 설계도를 다시 올립니다. 이미 있는 것은 건드리지 않습니다. */
    public static void reinstall() {
        synchronized (LOCK) {
            for (Blueprint blueprint : BY_TOWER_ID.values()) {
                if (ProductionTowerCatalog.find(blueprint.towerId()).isEmpty()) {
                    register(blueprint);
                }
            }
        }
    }

    /** 설계도 이름: 앞뒤 공백을 떼고, 서식 문자(&lt;, &gt;, §, \)를 빼고, 16자까지. */
    public static String sanitizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String cleaned = rawName.replaceAll("[<>§\\\\]", "").strip();
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_NAME_LENGTH) {
            return "";
        }
        return cleaned;
    }

    static String towerId(UUID owner, int number) {
        return BlueprintTowers.ID_PREFIX + owner.toString().replace("-", "").substring(0, 8) + "_" + number;
    }

    private static void register(Blueprint blueprint) {
        ProductionTowerCatalog.registerStarter(blueprint.towerType(), BlueprintTower::new);
    }
}
