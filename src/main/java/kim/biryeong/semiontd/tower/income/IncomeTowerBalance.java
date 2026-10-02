package kim.biryeong.semiontd.tower.income;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kim.biryeong.semiontd.config.RoundWaveConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;

/**
 * 인컴 타워 수치.
 *
 * <p>인컴 타워는 침공군 유닛 하나를 레인에 세워 두는 설비입니다. 설치·레벨업에 에메랄드를 내고,
 * 가진 동안 레벨만큼 라운드 인컴을 올리며, 준비 시간이 끝날 때마다 그 유닛을 적 레인으로 한 마리씩
 * 보냅니다. 유닛 기본 능력치와 설치 비용·레벨당 인컴은 {@code summons.json}의 같은 id 항목에서 읽습니다.
 */
public final class IncomeTowerBalance {
    /** 인컴 타워로 세울 수 있는 유닛. 창에 이 순서로 나옵니다. */
    public static final List<String> UNIT_IDS = List.of(
            "goblin_scout",
            "elf_assassin",
            "dark_priest",
            "dwarf_gunner",
            "troll_javelineer",
            "orc_warrior",
            "necromancer",
            "creaking",
            "siege_golem",
            "legion_commander",
            "ogre_champion"
    );

    public static final String TOWER_ID_PREFIX = "income_";
    public static final int MAX_LEVEL = 5;
    /** 보내는 유닛의 체력·공격력 전체 배율. 인컴 타워 유닛이 웨이브 몹보다 너무 세서 낮췄습니다. */
    public static final double BASE_STAT_MULTIPLIER = 0.85;
    /** 레벨마다 보내는 유닛의 체력·공격력이 기본치의 이만큼씩 더해집니다. */
    public static final double STAT_BONUS_PER_LEVEL = 0.35;
    /** 판매 시 낸 에메랄드 중 돌려받는 비율. */
    public static final double SELL_REFUND_RATE = 0.50;
    /** 라운드별 웨이브 세기를 볼 때, 보스 라운드 한 번이 튀지 않도록 이만큼의 라운드 중앙값을 씁니다. */
    private static final int WAVE_SMOOTHING_ROUNDS = 3;

    private IncomeTowerBalance() {
    }

    public static boolean isUnit(String summonId) {
        return UNIT_IDS.contains(summonId);
    }

    public static String towerId(String summonId) {
        return TOWER_ID_PREFIX + summonId;
    }

    /**
     * 인컴 타워가 차지하는 타워 수(인구)의 기본값. 비싼 유닛일수록 많이 차지해, 가장 비싼 유닛을 한도만큼
     * 깔아 버리는 도배를 막습니다(T1·T2 1, T3·T4 2, T5 3). 실제 값은 {@code tower_balance.json}의
     * {@code income_<유닛>.towerSlotCost}입니다.
     */
    public static final java.util.Map<String, Integer> DEFAULT_SLOT_COSTS = java.util.Map.ofEntries(
            java.util.Map.entry("goblin_scout", 1),
            java.util.Map.entry("elf_assassin", 1),
            java.util.Map.entry("dark_priest", 1),
            java.util.Map.entry("dwarf_gunner", 2),
            java.util.Map.entry("troll_javelineer", 2),
            java.util.Map.entry("orc_warrior", 2),
            java.util.Map.entry("necromancer", 2),
            java.util.Map.entry("creaking", 2),
            java.util.Map.entry("siege_golem", 2),
            java.util.Map.entry("legion_commander", 3),
            java.util.Map.entry("ogre_champion", 3)
    );

    /** 현재 레벨에서 다음 레벨로 올리는 비용. 1→2는 설치비, 이후 레벨마다 설치비의 절반씩 비싸집니다. */
    public static long upgradeCost(long buildCost, int currentLevel) {
        return Math.round(Math.max(0, buildCost) * 0.5 * (Math.max(1, currentLevel) + 1));
    }

    /** 그 레벨의 타워가 올려 두는 라운드 인컴. */
    public static long incomeAt(long incomePerLevel, int level) {
        return Math.max(0, incomePerLevel) * Math.max(1, level);
    }

    /** 레벨에 따른 유닛 체력·공격력 배율(전체 배율 포함). 1레벨 0.85배, 5레벨 2.04배입니다. */
    public static double statMultiplier(int level) {
        return BASE_STAT_MULTIPLIER * (1.0 + STAT_BONUS_PER_LEVEL * (Math.max(1, level) - 1));
    }

    public static long sellRefund(long paidEmerald) {
        return Math.round(Math.max(0, paidEmerald) * SELL_REFUND_RATE);
    }

    /**
     * 기본 레인 몹이 라운드마다 세지는 만큼 인컴 유닛도 세지게 하는 배율.
     *
     * <p>그 라운드 웨이브 몹의 평균 체력·공격력을 1라운드 평균으로 나눕니다. 15라운드 같은 보스 라운드가
     * 한 번 튀지 않도록 최근 몇 라운드의 중앙값을 쓰고, 앞 라운드보다 약해지지 않도록 누적 최댓값을 씁니다.
     */
    public static WaveScale waveScale(WaveConfig config, int round) {
        if (config == null || round <= 1) {
            return WaveScale.NONE;
        }
        WaveStrength base = strength(config, 1);
        if (base == null || base.health() <= 0.0 || base.attackDamage() <= 0.0) {
            return WaveScale.NONE;
        }
        List<WaveStrength> history = new ArrayList<>();
        double health = 1.0;
        double attackDamage = 1.0;
        for (int current = 1; current <= round; current++) {
            WaveStrength strength = strength(config, current);
            if (strength == null) {
                continue;
            }
            history.add(strength);
            List<WaveStrength> window = history.subList(Math.max(0, history.size() - WAVE_SMOOTHING_ROUNDS), history.size());
            health = Math.max(health, median(window.stream().mapToDouble(WaveStrength::health).toArray()) / base.health());
            attackDamage = Math.max(attackDamage,
                    median(window.stream().mapToDouble(WaveStrength::attackDamage).toArray()) / base.attackDamage());
        }
        return new WaveScale(health, attackDamage);
    }

    private static WaveStrength strength(WaveConfig config, int round) {
        RoundWaveConfig wave = config.configForRound(round).orElse(null);
        if (wave == null) {
            return null;
        }
        double count = 0.0;
        double health = 0.0;
        double attackDamage = 0.0;
        for (List<WaveMonsterEntry> entries : wave.lanes().values()) {
            for (WaveMonsterEntry entry : entries) {
                count += entry.count();
                health += entry.health() * entry.count();
                attackDamage += entry.attackDamage() * entry.count();
            }
        }
        return count <= 0.0 ? null : new WaveStrength(health / count, attackDamage / count);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    private record WaveStrength(double health, double attackDamage) {
    }

    public record WaveScale(double health, double attackDamage) {
        public static final WaveScale NONE = new WaveScale(1.0, 1.0);
    }
}
