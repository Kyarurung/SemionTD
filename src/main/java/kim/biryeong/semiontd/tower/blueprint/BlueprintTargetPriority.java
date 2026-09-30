package kim.biryeong.semiontd.tower.blueprint;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;

/**
 * 공격 대상 우선도. 힘을 더하는 것이 아니라 고르는 기준이라 가격에 들지 않습니다.
 * {@link #FIRST}는 기본 규칙(레인을 가장 많이 나아간 적)을 그대로 씁니다.
 */
public enum BlueprintTargetPriority {
    FIRST("first", "선두"),
    /** 최대 체력이 가장 높은 적(저격 캣과 같은 규칙). */
    STRONGEST("strongest", "최대 체력"),
    WEAKEST("weakest", "낮은 체력"),
    NEAREST("nearest", "가까운 적"),
    /** 다른 팀이 보낸 인컴 몹을 먼저, 없으면 기본 규칙. */
    SENT_FIRST("sent", "인컴 우선");

    private final String id;
    private final String displayName;

    BlueprintTargetPriority(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** 비어 있으면 기본 규칙을 따릅니다. */
    public Optional<SemionMonsterEntity> select(SemionTowerEntity source, List<SemionMonsterEntity> candidates) {
        if (candidates == null || candidates.isEmpty() || this == FIRST) {
            return Optional.empty();
        }
        List<SemionMonsterEntity> alive = candidates.stream().filter(candidate -> candidate != null && candidate.isAlive()).toList();
        return switch (this) {
            case STRONGEST -> alive.stream().max(Comparator.comparingDouble(BlueprintTargetPriority::maxHealth));
            case WEAKEST -> alive.stream().min(Comparator.comparingDouble(BlueprintTargetPriority::health));
            case NEAREST -> source == null ? Optional.empty()
                    : alive.stream().min(Comparator.comparingDouble(source::distanceToSqr));
            case SENT_FIRST -> alive.stream()
                    .filter(candidate -> candidate.runtimeMonster() != null && candidate.runtimeMonster().senderTeam().isPresent())
                    .max(Comparator.comparingDouble(candidate -> candidate.runtimeMonster().laneProgress()));
            case FIRST -> Optional.empty();
        };
    }

    private static double maxHealth(SemionMonsterEntity entity) {
        Monster monster = entity.runtimeMonster();
        return monster == null ? entity.getMaxHealth() : monster.maxHealth();
    }

    private static double health(SemionMonsterEntity entity) {
        Monster monster = entity.runtimeMonster();
        return monster == null ? entity.getHealth() : monster.health();
    }

    public static Optional<BlueprintTargetPriority> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(priority -> priority.id.equals(normalized)).findFirst();
    }
}
