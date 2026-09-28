package kim.biryeong.semiontd.entity.monster;

import net.minecraft.world.entity.LivingEntity;

/**
 * 몬스터 한 번의 공격이 언제, 어떻게 들어가는지.
 *
 * <p>기본 몬스터는 공격 애니메이션을 트는 틱에 바로 피해를 줍니다. 공격 방식이 있는 몬스터는
 * {@link #hitDelayTicks()}만큼 기다렸다가(애니메이션에서 무기가 닿는 순간) {@link #hit}을 부릅니다.
 * 모든 값은 틱 단위라 게임 배속(서버 틱 속도)이 바뀌어도 모션과 피해가 같이 빨라집니다.
 */
public interface MonsterAttackStyle {
    /** 공격 애니메이션을 틀고 피해가 들어가기까지의 틱. */
    int hitDelayTicks();

    /** 무기가 닿는 순간. {@code target}은 공격을 시작할 때 노리던 대상입니다(이미 죽었을 수 있음). */
    void hit(SemionMonsterEntity attacker, LivingEntity target);

    /** 바닐라 근접 공격 한 번(방어·저항은 대상 쪽에서 적용됩니다). */
    static void strike(SemionMonsterEntity attacker, LivingEntity target, double damage) {
        if (target != null && target.isAlive() && !target.isRemoved()) {
            target.hurt(attacker.damageSources().mobAttack(attacker), (float) damage);
        }
    }
}
