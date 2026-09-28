package kim.biryeong.semiontd.summon.invasion;

import java.util.Map;

/**
 * 침공군 유닛별 전투 틀. 공격 간격은 공격 애니메이션 길이에 맞추고, 피해 틱은 애니메이션에서 무기가 닿는 순간입니다
 * (tools/invasion-models/bb/*_anims.js의 attack 키프레임, 1초 = 20틱). 기존 소환 몹보다 간격이 길어진 만큼 한 방
 * 피해를 키워 초당 피해는 비슷하게 맞췄습니다(summons.json의 attackDamage).
 */
public final class InvasionUnits {
    /** 강령술사가 불러내는 해골. 소환 목록에는 없고 보상도 없습니다. */
    public static final String NECROMANCER_MINION = "necromancer_skeleton";

    /**
     * @param moveSpeed    이동 속도 배율(기본 1.0)
     * @param attackRange  공격 사거리(블록)
     * @param intervalTicks 공격 간격(틱) = 공격 애니메이션 길이
     * @param hitDelayTicks 공격을 시작해 피해가 들어가기까지(틱) = 무기가 닿는 키프레임
     */
    public record Profile(double moveSpeed, double attackRange, int intervalTicks, int hitDelayTicks) {
    }

    private static final Map<String, Profile> PROFILES = Map.of(
            // 고블린: 가장 빠르고 싼 돌격병. 공격 0.55초, 칼이 닿는 0.28초.
            "goblin_scout", new Profile(1.4, 2.5, 11, 6),
            // 엘프: 빠른 발과 빠른 칼. 공격 0.6초, 베는 0.22초.
            "elf_assassin", new Profile(1.35, 2.5, 10, 4),
            // 암흑 신관: 지팡이를 치켜들었다 내리는 0.7초에 터집니다. 공격 1.3초.
            "dark_priest", new Profile(1.0, 6.0, 26, 14),
            // 드워프: 조준했다 쏘는 0.4초. 공격 0.9초. 관통탄이라 사거리가 조금 깁니다.
            "dwarf_gunner", new Profile(0.95, 7.0, 18, 8),
            // 트롤: 창을 놓는 0.4초부터 날아갑니다. 공격 1.0초.
            "troll_javelineer", new Profile(1.0, 6.5, 20, 8),
            // 오크: 도끼가 떨어지는 0.5초. 공격 0.9초.
            "orc_warrior", new Profile(1.05, 2.5, 18, 10),
            // 강령술사: 지팡이를 내리꽂는 0.8초. 공격 1.4초.
            "necromancer", new Profile(0.95, 6.0, 28, 16),
            // 공성 골렘: 공성추가 닿는 0.7초. 공격 1.4초. 느리게 걸어 보스만 칩니다.
            "siege_golem", new Profile(0.85, 2.8, 28, 14),
            // 군단장: 내려베는 0.62초. 공격 1.2초.
            "legion_commander", new Profile(0.9, 2.8, 24, 12),
            // 오우거: 몽둥이가 땅에 닿는 0.8초. 공격 1.5초.
            "ogre_champion", new Profile(0.85, 3.0, 30, 16)
    );

    private InvasionUnits() {
    }

    public static boolean isUnit(String id) {
        return PROFILES.containsKey(id);
    }

    public static Profile profile(String id) {
        Profile profile = PROFILES.get(id);
        if (profile == null) {
            throw new IllegalArgumentException("Not an invasion unit: " + id);
        }
        return profile;
    }
}
