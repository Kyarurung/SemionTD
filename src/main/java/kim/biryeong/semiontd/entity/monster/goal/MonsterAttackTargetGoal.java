package kim.biryeong.semiontd.entity.monster.goal;

import java.util.EnumSet;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.goal.CombatCooldown;
import kim.biryeong.semiontd.game.CombatSpeedRuntime;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class MonsterAttackTargetGoal extends Goal {
    private final SemionMonsterEntity monster;
    private final double speedModifier;
    private final CombatCooldown cooldown = new CombatCooldown();

    public MonsterAttackTargetGoal(SemionMonsterEntity monster, double speedModifier) {
        this.monster = monster;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return monster.isAlive() && (monster.schoolSpells().controlled()
                || monster.getTarget() != null && monster.getTarget().isAlive());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (SuccubusDreams.isAsleep(monster)) {
            monster.getNavigation().stop();
            monster.playAnimation(SemionAnimationState.IDLE);
            return;
        }
        cooldown.advance(CombatSpeedRuntime.multiplier(monster.level()));
        if (monster.isStunned()) {
            monster.getNavigation().stop();
            monster.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        boolean controlled = monster.schoolSpells().controlled();
        LivingEntity target = controlled ? monster.schoolSpells().controlTarget() : monster.getTarget();
        if (target == null || !target.isAlive()) {
            monster.playAnimation(SemionAnimationState.IDLE);
            return;
        }
        if (!controlled && !monster.canTargetDefense(target)) {
            monster.setTarget(null);
            monster.getNavigation().stop();
            monster.playAnimation(SemionAnimationState.WALK);
            return;
        }

        monster.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double attackRange = monster.attackRange();
        double attackRangeSqr = attackRange * attackRange;
        double distanceSqr = monster.distanceToSqr(target);
        if (distanceSqr > attackRangeSqr) {
            monster.playAnimation(SemionAnimationState.WALK);
            double adjustedSpeed = speedModifier * monster.movementSpeedMultiplier();
            monster.getNavigation().moveTo(target, adjustedSpeed);
            monster.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), adjustedSpeed);
            return;
        }

        monster.getNavigation().stop();
        if (!cooldown.ready() || monster.isDisarmed()) {
            monster.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        for (int event = 0; event < CombatCooldown.MAX_EVENTS && cooldown.ready()
                && monster.isAlive() && !monster.isRemoved() && target.isAlive() && !target.isRemoved()
                && !monster.isStunned() && !monster.isDisarmed() && !SuccubusDreams.isAsleep(monster); event++) {
            if (controlled && target instanceof SemionMonsterEntity ally) {
                monster.schoolSpells().attackControlled(ally);
            } else {
                monster.startAttack(target);
            }
            cooldown.restart(monster.attackIntervalTicks());
        }
    }
}
