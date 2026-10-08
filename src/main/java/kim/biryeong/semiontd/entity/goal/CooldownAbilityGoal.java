package kim.biryeong.semiontd.entity.goal;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.CombatSpeedRuntime;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

public abstract class CooldownAbilityGoal extends Goal {
    private final PathfinderMob caster;
    private final int cooldownTicks;
    private final int retryDelayTicks;
    private final CombatCooldown cooldown = new CombatCooldown();

    protected CooldownAbilityGoal(PathfinderMob caster, int cooldownTicks, int retryDelayTicks) {
        if (cooldownTicks <= 0 || retryDelayTicks <= 0) {
            throw new IllegalArgumentException("Ability cooldown and retry delay must be positive.");
        }
        this.caster = caster;
        this.cooldownTicks = cooldownTicks;
        this.retryDelayTicks = retryDelayTicks;
    }

    @Override
    public boolean canUse() {
        return caster.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        cooldown.advance(CombatSpeedRuntime.multiplier(caster.level()));
        if (caster instanceof SemionMonsterEntity monster && monster.isStunned()) {
            return;
        }

        for (int event = 0; event < CombatCooldown.MAX_EVENTS && cooldown.ready() && canUse(); event++) {
            cooldown.restart((castAbility() ? cooldownTicks : retryDelayTicks) + 1.0);
        }
    }

    protected abstract boolean castAbility();
}
