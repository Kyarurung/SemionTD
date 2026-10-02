package kim.biryeong.semiontd.tower.blueprint;

import kim.biryeong.semiontd.tower.TowerType;

/**
 * 빌더 빌더가 설계한 타워의 id 규칙과 설정 키.
 *
 * <p>설계도 타워는 상수로 정의되지 않고 경기 중에 만들어집니다. id는 {@code blueprint_<주인 앞 8자리>_<번호>}이고,
 * 가격·한도 계수는 타워가 아닌 설정 항목 {@link #CONFIG_ID} 아래에 둡니다.
 */
public final class BlueprintTowers {
    public static final String ID_PREFIX = "blueprint_";
    /** 가격·한도 계수를 담는 tower_balance.json 능력 항목(타워 아님). 타워 id 접두사와 겹치지 않게 둡니다. */
    public static final String CONFIG_ID = "builder_blueprint";

    private BlueprintTowers() {
    }

    public static boolean isBlueprintId(String towerId) {
        return towerId != null && towerId.startsWith(ID_PREFIX);
    }

    public static boolean isBlueprintTower(TowerType type) {
        return type != null && isBlueprintId(type.id());
    }
}
