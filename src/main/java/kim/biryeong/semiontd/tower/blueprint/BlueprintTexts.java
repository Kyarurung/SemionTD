package kim.biryeong.semiontd.tower.blueprint;

import java.util.Locale;

/** 모듈 단계가 실제로 무엇을 하는지 한 줄로 풀어 줍니다. 편집 창과 타워 설명이 함께 씁니다. */
public final class BlueprintTexts {
    private BlueprintTexts() {
    }

    public static String effect(BlueprintModule module, int level) {
        return switch (module) {
            case MULTISHOT -> "추가 대상 " + whole(module.value("extraTargets", level)) + " · 피해 " + pct(module.value("damageRatio", level));
            case SPLASH -> "반경 " + num(module.value("radius", level)) + " · 피해 " + pct(module.value("damageRatio", level));
            case CHAIN -> "튀는 수 " + whole(module.value("targets", level)) + " · 피해 " + pct(module.value("damageRatio", level));
            case SLOW -> "둔화 " + pct(module.value("amount", level)) + " · " + seconds(module.value("durationTicks", level));
            case STUN -> "확률 " + pct(module.value("chance", level)) + " · " + seconds(module.value("durationTicks", level));
            case POISON -> "준 피해의 " + pct(module.value("damageRatio", level)) + "를 " + seconds(module.value("durationTicks", level)) + " 동안";
            case VULNERABILITY -> "받는 피해 +" + pct(module.value("amount", level)) + " · " + seconds(module.value("durationTicks", level));
            case CRIT -> "확률 " + pct(module.value("chance", level)) + " · ×" + num(module.value("multiplier", level));
            case EXECUTE -> "체력 " + pct(module.value("threshold", level)) + " 이하 적 피해 +" + pct(module.value("damageBonus", level));
            case LIFESTEAL -> "준 피해의 " + pct(module.value("ratio", level)) + " 회복";
            case THORNS -> "받은 피해의 " + pct(module.value("reflectRatio", level)) + " 반사 · 반경 " + num(module.value("radius", level));
            case ARMOR -> "받는 피해 -" + pct(module.value("reduction", level));
            case REGEN -> "초당 최대 체력 " + pct(module.value("maxHealthPerSecond", level));
            case HEAL_AURA -> "3초마다 반경 " + num(module.value("radius", level)) + " 아군에 내 최대 체력 "
                    + pct(module.value("maxHealthRatio", level)) + " 치유";
            case HASTE_AURA -> "반경 " + num(module.value("radius", level)) + " 아군 공격 속도 +" + pct(module.value("attackSpeedBonus", level));
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
