package kim.biryeong.semiontd.tower.blueprint;

import java.util.Locale;

/** 모듈 단계가 실제로 무엇을 하는지 한 줄로 풀어 줍니다. 편집 창과 타워 설명이 함께 씁니다. */
public final class BlueprintTexts {
    private BlueprintTexts() {
    }

    public static String basicDps(BlueprintStats stats) {
        return "기본 초당 피해 " + num(stats.damagePerSecond());
    }

    public static String effect(BlueprintModule module, int level) {
        return switch (module) {
            case MULTISHOT -> "추가 대상 " + whole(module.value("extraTargets", level)) + " · 피해 " + pct(module.value("damageRatio", level));
            case SPLASH -> "반경 " + num(module.value("radius", level)) + " · 피해 " + pct(module.value("damageRatio", level));
            case LINE -> "사거리 +" + num(module.value("lengthBonus", level)) + " · 폭 " + num(module.value("width", level))
                    + " · 피해 " + pct(module.value("damageRatio", level));
            case CHAIN -> "튀는 수 " + whole(module.value("targets", level)) + " · 피해 " + pct(module.value("damageRatio", level));
            case SLOW -> "둔화 " + pct(module.value("amount", level)) + " · " + seconds(module.value("durationTicks", level));
            case STUN -> "확률 " + pct(module.value("chance", level)) + " · " + seconds(module.value("durationTicks", level));
            case POISON -> "준 피해의 " + pct(module.value("damageRatio", level)) + "를 " + seconds(module.value("durationTicks", level)) + " 동안";
            case VULNERABILITY -> "받는 피해 +" + pct(module.value("amount", level)) + " · " + seconds(module.value("durationTicks", level));
            case CRIT -> "확률 " + pct(module.value("chance", level)) + " · ×" + num(module.value("multiplier", level));
            case EXECUTE -> "체력 " + pct(module.value("threshold", level)) + " 이하 적 피해 +" + pct(module.value("damageBonus", level));
            case KILL_EXPLOSION -> "처치한 적이 반경 " + num(module.value("radius", level)) + " 폭발 · 마지막 피해의 "
                    + pct(module.value("damageRatio", level));
            case LIFESTEAL -> "준 피해의 " + pct(module.value("ratio", level)) + " 회복";
            case THORNS -> "받은 피해의 " + pct(module.value("reflectRatio", level)) + " 반사 · 반경 " + num(module.value("radius", level));
            case ARMOR -> "받는 피해 -" + pct(module.value("reduction", level));
            case REGEN -> "초당 최대 체력 " + pct(module.value("maxHealthPerSecond", level));
            case HEAL_AURA -> "3초마다 반경 " + num(module.value("radius", level)) + " 아군에 내 최대 체력 "
                    + pct(module.value("maxHealthRatio", level)) + " 치유";
            case HASTE_AURA -> "반경 " + num(module.value("radius", level)) + " 아군 공격 속도 +" + pct(module.value("attackSpeedBonus", level));
            case DETECTION -> "사거리 +" + num(module.value("radiusBonus", level)) + " 안의 은신한 적을 드러냄";
            case BOSS_SLAYER -> "보스 피해 +" + pct(module.value("bossBonus", level)) + " · 탱커 +"
                    + pct(module.value("bossBonus", level) * module.value("tankRatio", level));
            case FOCUS -> "같은 적 연속 공격마다 +" + pct(module.value("perStack", level)) + " (최대 "
                    + whole(module.value("maxStacks", level)) + "번)";
            case FRENZY -> "공격마다 공격 속도 +" + pct(module.value("perStack", level)) + " (최대 "
                    + whole(module.value("maxStacks", level)) + "번, 라운드마다 초기화)";
            case KNOCKBACK -> "확률 " + pct(module.value("chance", level)) + " · " + num(module.value("distance", level)) + "칸 밀어냄";
            case PLUNDER -> "처치 시 " + pct(module.value("chance", level)) + " 확률로 다이아 " + whole(module.value("diamonds", level));
            case TAUNT -> "어그로 +" + whole(module.value("aggroBonus", level)) + " · 받는 피해 -" + pct(module.value("reduction", level));
            case RANGE_AURA -> "반경 " + num(module.value("radius", level)) + " 아군 사거리 +" + num(module.value("rangeBonus", level));
            case SUMMON -> num(module.value("intervalTicks", level) / 20.0) + "초마다 하수인(능력치 " + pct(module.value("statRatio", level))
                    + ") · " + num(module.value("durationTicks", level) / 20.0) + "초 유지 · 최대 " + whole(module.value("count", level)) + "기";
        };
    }

    public static String pct(double ratio) {
        double percent = ratio * 100.0;
        return (percent == Math.rint(percent) ? Long.toString((long) percent) : String.format(Locale.ROOT, "%.1f", percent)) + "%";
    }

    public static String num(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String whole(double value) {
        return Long.toString(Math.round(value));
    }

    private static String seconds(double ticks) {
        return num(ticks / 20.0) + "초";
    }
}
