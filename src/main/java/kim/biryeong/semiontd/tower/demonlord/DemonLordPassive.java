package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import java.util.Locale;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * [스킬 배정] 창의 8·9번 슬롯에 넣는 패시브.
 *
 * <p>스킬과 달리 키에 묶이지 않고, 넣어 두기만 하면 항상 켜져 있습니다. 다이아로 사고, 빼면 낸 값을 전부
 * 돌려받습니다. 같은 패시브는 한 슬롯에만 둘 수 있습니다. 수치는 {@code tower_balance.json}의
 * {@code demon_lord_passive_<key>} 능력치에서 읽습니다.
 */
public enum DemonLordPassive {
    /** 마검 평타가 대상 주변까지 베고, 준 피해의 일부만큼 회복합니다. */
    BLOOD_CLEAVE("blood_cleave", "흡혈 참격", Items.REDSTONE),
    /** 웨이브마다 아군 타워 몇 개를 복제해 내 라인에 잠시 세웁니다. */
    LEGION_ECHO("legion_echo", "군단의 잔영", Items.ARMOR_STAND),
    /** 내 라인과 전투 구역 밖으로 나가 아군 라인의 적과도 싸울 수 있습니다. */
    BOUNDLESS("boundless", "경계 없는 마왕", Items.ENDER_PEARL);

    private final String key;
    private final String displayName;
    private final Item item;

    DemonLordPassive(String key, String displayName, Item item) {
        this.key = key;
        this.displayName = displayName;
        this.item = item;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Item item() {
        return item;
    }

    public String configId() {
        return "demon_lord_passive_" + key;
    }

    public double ability(String name, double fallback) {
        return TowerBalanceRuntime.ability(configId(), name, fallback);
    }

    public long cost() {
        return Math.max(0L, Math.round(ability("cost", 150.0)));
    }

    /** 창에 보여 줄 설명. 현재 설정값을 채워 넣습니다. */
    public List<String> description() {
        return switch (this) {
            case BLOOD_CLEAVE -> List.of(
                    "마검 평타가 대상 주변 " + number(ability("cleaveRadius", 2.5)) + "칸까지 베어,",
                    "주변 적에게 평타 피해의 " + percent(ability("cleaveRatio", 0.6)) + "를 줍니다.",
                    "준 피해의 " + percent(ability("lifeStealRatio", 0.15)) + "만큼 회복합니다"
                            + " (한 번에 최대 체력의 " + percent(ability("lifeStealCap", 0.04)) + "까지).",
                    "스킬 범위 스탯을 받으면 베는 범위도 넓어집니다."
            );
            case LEGION_ECHO -> List.of(
                    "웨이브가 시작될 때마다 아군 라인의 타워 " + (int) ability("copies", 5.0) + "개를",
                    "무작위로 복제해 내 라인에 그 웨이브 동안 세웁니다.",
                    "한 플레이어에 하나뿐인 타워(흑마법사, 엔더 드래곤 등)와",
                    "싸우지 않는 타워는 복제하지 않습니다. 아군이 없으면 효과가 없습니다."
            );
            case BOUNDLESS -> List.of(
                    "내 라인과 전투 구역 밖으로 자유롭게 나갈 수 있습니다.",
                    "같은 팀 아군 라인의 적도 공격하고, 그 적도 나를 노립니다."
            );
        };
    }

    public static DemonLordPassive fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (DemonLordPassive passive : values()) {
            if (passive.key.equals(normalized)) {
                return passive;
            }
        }
        return null;
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(double ratio) {
        return number(ratio * 100.0) + "%";
    }
}
