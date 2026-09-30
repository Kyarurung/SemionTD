package kim.biryeong.semiontd.tower.blueprint;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 설계도에 붙이는 능력 모듈. 모듈마다 1~3단계가 있고, 수치는 {@link BlueprintTowers#CONFIG_ID} 아래
 * {@code <id>.<항목>} 키로 설정에서 바꿉니다. 단계 값은 {@code base + perLevel × (단계 - 1)}로 셉니다.
 *
 * <p>가격에는 세 갈래로 들어갑니다. 공격 모듈마다 공격 쪽 위력에 {@code (1 + offenseWeight × 단계)}를 곱하고,
 * 생존 모듈마다 생존 쪽에 {@code (1 + defenseWeight × 단계)}를 곱하며, 지원 모듈은 위력에 {@code powerPerLevel × 단계}를
 * 더합니다. 모듈끼리 서로 겹쳐 세지므로(다중 사격 화살도 광역·연쇄가 터짐) 곱으로 셉니다. 둔화·기절·취약 같은 유틸 모듈은
 * 피해가 0이어도 걸리므로 {@code utilityWeight × 단계 × √(초당 맞힘) × 대상 수 보정}을 위력에 더합니다.
 */
public enum BlueprintModule {
    /** 기본 공격 때 주변 적 몇에게 같은 공격을 한 번 더(스켈레톤 계열). */
    MULTISHOT("multishot", "다중 사격", Kind.OFFENSE),
    /** 맞은 적 주변에 피해를 퍼뜨립니다. */
    SPLASH("splash", "광역", Kind.OFFENSE),
    /** 타워에서 맞은 적 쪽으로 뻗는 직선 위의 적 모두에게 피해(럴커식). */
    LINE("line", "직선 관통", Kind.OFFENSE),
    /** 맞은 적에게서 가까운 적으로 튀는 번개. */
    CHAIN("chain", "연쇄", Kind.OFFENSE),
    /** 맞은 적의 이동 속도를 잠시 낮춥니다. */
    SLOW("slow", "둔화", Kind.UTILITY),
    /** 일정 확률로 맞은 적을 잠시 기절시킵니다(같은 적은 한동안 다시 기절하지 않음). */
    STUN("stun", "기절", Kind.UTILITY),
    /** 맞은 적에게 독을 겁니다(겹쳐 쌓임). */
    POISON("poison", "독", Kind.OFFENSE),
    /** 맞은 적이 타워에게 받는 피해가 잠시 늘어납니다. */
    VULNERABILITY("vulnerability", "취약", Kind.UTILITY),
    /** 일정 확률로 피해가 크게 들어갑니다. */
    CRIT("crit", "치명타", Kind.OFFENSE),
    /** 체력이 적게 남은 적에게 피해가 늘어납니다. */
    EXECUTE("execute", "처형", Kind.OFFENSE),
    /** 처치한 적이 터져 주변 적에게 피해를 줍니다(라클 캣). 폭발로 죽은 적은 다시 터지지 않습니다. */
    KILL_EXPLOSION("kill_explosion", "처치 폭발", Kind.OFFENSE),
    /** 준 피해의 일부만큼 체력을 회복합니다. 준 피해에 비례하므로 값은 공격 쪽으로 셉니다. */
    LIFESTEAL("lifesteal", "흡혈", Kind.OFFENSE),
    /** 맞으면 주변 적에게 받은 피해의 일부를 돌려줍니다. */
    THORNS("thorns", "가시", Kind.DEFENSE),
    /** 받는 피해를 줄입니다. */
    ARMOR("armor", "방호", Kind.DEFENSE),
    /** 매초 최대 체력의 일부를 회복합니다. */
    REGEN("regen", "재생", Kind.DEFENSE),
    /** 주변 아군 타워를 주기적으로 치유합니다. */
    HEAL_AURA("heal_aura", "치유 오라", Kind.SUPPORT),
    /** 주변 아군 타워의 공격 속도를 올립니다. */
    HASTE_AURA("haste_aura", "가속 오라", Kind.SUPPORT);

    public static final int MAX_LEVEL = 3;

    public enum Kind {
        /** 공격 쪽 위력에 곱해짐. */
        OFFENSE,
        /** 생존 쪽 위력에 곱해짐. */
        DEFENSE,
        /** 맞힐 때마다 거는 효과(둔화·기절·취약). 피해와 상관없이 걸리므로 초당 맞힘 수 × 맞히는 대상 수로 값을 셉니다. */
        UTILITY,
        /** 주변 아군을 돕는 오라. 위력에 더해짐. */
        SUPPORT
    }

    private final String id;
    private final String displayName;
    private final Kind kind;

    BlueprintModule(String id, String displayName, Kind kind) {
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Kind kind() {
        return kind;
    }

    /** 이 모듈 단계의 수치. 키가 {@code <id>.<param>}이면 그 값, {@code <id>.<param>PerLevel}이 단계당 증가분입니다. */
    public double value(String param, int level) {
        double base = BlueprintPricing.value(id + "." + param);
        double perLevel = BlueprintPricing.value(id + "." + param + "PerLevel");
        return base + perLevel * Math.max(0, level - 1);
    }

    /** 가격 공식에서 단계당 더하는 가중치. */
    public double priceWeight() {
        return BlueprintPricing.value(id + "." + switch (kind) {
            case OFFENSE -> "offenseWeight";
            case DEFENSE -> "defenseWeight";
            case UTILITY -> "utilityWeight";
            case SUPPORT -> "powerPerLevel";
        });
    }

    public static Optional<BlueprintModule> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(module -> module.id.equals(normalized)).findFirst();
    }
}
