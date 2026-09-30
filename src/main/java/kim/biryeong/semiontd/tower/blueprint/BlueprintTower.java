package kim.biryeong.semiontd.tower.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.tower.thunder.ThunderVfx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;

/**
 * 설계도로 세운 타워. 능력치는 설계도가 만든 타입에서 오고, 모듈은 이 클래스가 기존 전투 훅에 붙여 돌립니다.
 *
 * <ul>
 *   <li>공격이 끝난 뒤({@link #onAttackResolved}): 다중 사격·광역·연쇄, 맞은 적에게 둔화·기절·독·취약, 흡혈.</li>
 *   <li>나가는 피해({@link #modifyOutgoingDamage}): 치명타·처형.</li>
 *   <li>받는 피해: 방호로 줄이고, 맞은 뒤 가시로 돌려줍니다.</li>
 *   <li>1초마다({@link #execute}): 재생, 그리고 3초마다 치유·가속 오라.</li>
 * </ul>
 * 디버프는 설계도(타워 id)마다 출처가 하나라, 같은 설계도 타워 여러 기가 같은 적에게 겹쳐 걸지 않습니다.
 */
public class BlueprintTower extends ProductionTower {
    private static final int PULSE_TICKS = 20;
    private static final int AURA_EVERY_PULSES = 3;

    private BlueprintStats cachedStats;
    private long lastThornsTick = Long.MIN_VALUE;
    private int pulseCount;

    public BlueprintTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    /** 설계도의 능력치·모듈. 설계도가 이미 지워졌으면(경기 종료 직전 등) 모듈 없는 타입 능력치만 씁니다. */
    public BlueprintStats blueprintStats() {
        if (cachedStats == null) {
            cachedStats = BlueprintStates.find(type()).map(Blueprint::stats)
                    .orElseGet(() -> new BlueprintStats(type().maxHealth(), type().damage(), type().attackIntervalTicks(),
                            type().range(), type().aggroPriority(), primaryDamageType()));
        }
        return cachedStats;
    }

    private int level(BlueprintModule module) {
        return blueprintStats().level(module);
    }

    private double value(BlueprintModule module, String param) {
        return module.value(param, level(module));
    }

    private ResourceLocation source(String effect) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "blueprint_" + effect + "/" + type().id());
    }

    // ------------------------------------------------------------------ targeting

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        return blueprintStats().targetPriority().select(towerEntity, candidates);
    }

    // ------------------------------------------------------------------ outgoing damage

    @Override
    public double modifyOutgoingDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double damage = damageAmount;
        if (level(BlueprintModule.EXECUTE) > 0 && target != null && target.runtimeMonster() != null) {
            Monster monster = target.runtimeMonster();
            if (monster.maxHealth() > 0.0 && monster.health() / monster.maxHealth() <= value(BlueprintModule.EXECUTE, "threshold")) {
                damage *= 1.0 + value(BlueprintModule.EXECUTE, "damageBonus");
            }
        }
        if (level(BlueprintModule.CRIT) > 0 && towerEntity != null
                && towerEntity.getRandom().nextDouble() < value(BlueprintModule.CRIT, "chance")) {
            damage *= value(BlueprintModule.CRIT, "multiplier");
        }
        return damage;
    }

    // ------------------------------------------------------------------ on hit

    @Override
    public void onAttackResolved(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        super.onAttackResolved(towerEntity, target, attemptedDamage, resolvedOutgoingDamage, dealtDamage, killedTarget);
        if (towerEntity == null || target == null) {
            return;
        }
        hitPackage(towerEntity, target, attemptedDamage, resolvedOutgoingDamage, dealtDamage, killedTarget);
        if (level(BlueprintModule.MULTISHOT) > 0) {
            multishot(towerEntity, target, attemptedDamage);
        }
    }

    /**
     * 한 번 맞힐 때마다 도는 모듈 묶음: 맞은 적 디버프, 광역, 연쇄, 흡혈. 기본 공격과 다중 사격 화살 하나하나가 모두
     * 이 묶음을 탑니다(그래서 광역+다중 사격+연쇄가 함께 터짐). 광역·연쇄로 맞은 적은 이 묶음을 다시 타지 않습니다.
     */
    private void hitPackage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double baseDamage,
            double outgoingDamage, double dealtDamage, boolean killed) {
        if (!killed && target.isAlive()) {
            applyOnHitEffects(towerEntity, target, dealtDamage);
        }
        if (level(BlueprintModule.SPLASH) > 0) {
            splash(towerEntity, target, baseDamage);
        }
        if (level(BlueprintModule.CHAIN) > 0) {
            chain(towerEntity, target, outgoingDamage);
        }
        if (level(BlueprintModule.LIFESTEAL) > 0 && dealtDamage > 0.0) {
            healTarget(towerEntity, dealtDamage * value(BlueprintModule.LIFESTEAL, "ratio"));
        }
    }

    /** 처치 폭발(라클 캣): 처치한 적 자리에서 터져 주변 적에게 마지막 피해의 일부를 줍니다. 폭발로 죽은 적은 터지지 않습니다. */
    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        super.onKill(towerEntity, target, damageAmount);
        if (level(BlueprintModule.KILL_EXPLOSION) <= 0 || towerEntity == null || target == null || damageAmount <= 0.0) {
            return;
        }
        double explosionDamage = damageAmount * value(BlueprintModule.KILL_EXPLOSION, "damageRatio");
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "kill_explosion"), towerEntity, target.position(),
                value(BlueprintModule.KILL_EXPLOSION, "radius"), Set.of(target.getUUID()), null,
                AreaVfxSpec.onTrigger(AreaVfxStyles.CORPSE_EXPLOSION));
        TowerAreaDamage.applyResolved(this, towerEntity, request,
                monster -> resolveBasicAttackOutgoingDamage(towerEntity, monster, explosionDamage), false,
                (monster, damage, killed) -> {
                }, primaryDamageType());
    }

    private void applyOnHitEffects(SemionTowerEntity towerEntity, SemionMonsterEntity target, double dealtDamage) {
        if (level(BlueprintModule.SLOW) > 0) {
            target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, source("slow"),
                    value(BlueprintModule.SLOW, "amount"), (int) value(BlueprintModule.SLOW, "durationTicks"));
        }
        if (level(BlueprintModule.VULNERABILITY) > 0) {
            target.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, source("vulnerability"),
                    value(BlueprintModule.VULNERABILITY, "amount"), (int) value(BlueprintModule.VULNERABILITY, "durationTicks"));
        }
        if (level(BlueprintModule.POISON) > 0 && dealtDamage > 0.0) {
            int duration = Math.max(1, (int) value(BlueprintModule.POISON, "durationTicks"));
            int interval = Math.max(1, (int) value(BlueprintModule.POISON, "tickIntervalTicks"));
            double total = dealtDamage * value(BlueprintModule.POISON, "damageRatio");
            double perTick = total / Math.max(1, duration / interval);
            target.applyBeePoison(ownerPlayer(), this, perTick, Math.max(1, (int) value(BlueprintModule.POISON, "maxStacks")),
                    duration, interval);
        }
        if (level(BlueprintModule.STUN) > 0 && towerEntity.getRandom().nextDouble() < value(BlueprintModule.STUN, "chance")
                && target.applyTimedEffect(TimedEffectType.MONSTER_STUN_IMMUNITY, source("stun_immunity"), 1.0,
                (int) value(BlueprintModule.STUN, "immunityTicks"))) {
            target.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1.0, (int) value(BlueprintModule.STUN, "durationTicks"));
            TowerVfxService.showAreaEffect(towerEntity, AreaEffectIds.tower(this, "stun"), AreaVfxStyles.DEBUFF,
                    target.position(), 0.8, List.of(target.position()), 1, 1, 0);
        }
    }

    /** 스켈레톤 계열처럼 사거리 안의 다른 적(가까운 순)에게 같은 공격을 한 번 더 겁니다. */
    private void multishot(SemionTowerEntity towerEntity, SemionMonsterEntity primary, double baseDamage) {
        int count = (int) value(BlueprintModule.MULTISHOT, "extraTargets");
        double ratio = value(BlueprintModule.MULTISHOT, "damageRatio");
        if (count <= 0 || ratio <= 0.0 || baseDamage <= 0.0) {
            return;
        }
        double range = towerEntity.attackRange();
        List<SemionMonsterEntity> candidates = new ArrayList<>(towerEntity.level().getEntitiesOfClass(
                SemionMonsterEntity.class,
                towerEntity.targetSearchBox(),
                monster -> monster.isAlive() && monster != primary && !monster.isDominated()
                        && monster.runtimeMonster() != null
                        && towerEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                        && towerEntity.distanceToSqr(monster) <= range * range));
        candidates.sort(Comparator.comparingDouble(primary::distanceToSqr));
        double shotDamage = baseDamage * ratio;
        for (SemionMonsterEntity extra : candidates.subList(0, Math.min(count, candidates.size()))) {
            Tower.DamageResult result = towerEntity.damageBasicAttackSecondaryTargetResult(extra, shotDamage);
            TowerVfxService.showSecondaryAttack(towerEntity, extra);
            if (result.killed()) {
                onKill(towerEntity, extra, shotDamage);
            }
            // 다중 사격 화살도 광역·연쇄·디버프·흡혈을 모두 탑니다.
            hitPackage(towerEntity, extra, shotDamage, result.outgoingDamage(), result.dealtDamage(), result.killed());
        }
    }

    private void splash(SemionTowerEntity towerEntity, SemionMonsterEntity target, double baseDamage) {
        double radius = value(BlueprintModule.SPLASH, "radius");
        double splashDamage = baseDamage * value(BlueprintModule.SPLASH, "damageRatio");
        if (radius <= 0.0 || splashDamage <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "splash"), towerEntity, target, (float) radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH));
        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            if (monster == target) {
                return AreaEffectOutcome.UNCHANGED;
            }
            boolean killed = towerEntity.damageBasicAttackSecondaryTargetResult(monster, splashDamage).killed();
            if (killed) {
                onKill(towerEntity, monster, splashDamage);
            }
            return killed ? AreaEffectOutcome.KILLED : AreaEffectOutcome.APPLIED;
        });
    }

    /** 번개 타워의 연쇄와 같은 방식: 맞은 적 주변 적 몇에게 나간 피해의 일부가 튑니다. */
    private void chain(SemionTowerEntity towerEntity, SemionMonsterEntity target, double outgoingDamage) {
        int jumps = (int) value(BlueprintModule.CHAIN, "targets");
        double radius = value(BlueprintModule.CHAIN, "radius");
        double chainDamage = outgoingDamage * value(BlueprintModule.CHAIN, "damageRatio");
        if (jumps <= 0 || radius <= 0.0 || chainDamage <= 0.0) {
            return;
        }
        AtomicInteger remaining = new AtomicInteger(jumps);
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "chain"), towerEntity, target.position(), radius,
                Set.of(target.getUUID()), null, AreaVfxSpec.onTrigger(ThunderVfx.ARC));
        TowerAreaDamage.applyResolved(this, towerEntity, request,
                monster -> remaining.getAndDecrement() > 0 ? chainDamage : 0.0, false, (monster, damage, killed) -> {
                    if (killed) {
                        onKill(towerEntity, monster, damage);
                    }
                }, primaryDamageType());
    }

    // ------------------------------------------------------------------ incoming damage

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (level(BlueprintModule.ARMOR) <= 0) {
            return damageAmount;
        }
        return damageAmount * Math.max(0.0, 1.0 - value(BlueprintModule.ARMOR, "reduction"));
    }

    @Override
    public void onDamaged(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount,
            double previousHealth, double currentHealth) {
        super.onDamaged(towerEntity, damageSource, damageAmount, previousHealth, currentHealth);
        if (level(BlueprintModule.THORNS) <= 0 || towerEntity == null || damageAmount <= 0.0) {
            return;
        }
        long now = towerEntity.level().getGameTime();
        if (lastThornsTick != Long.MIN_VALUE && now - lastThornsTick < (long) value(BlueprintModule.THORNS, "cooldownTicks")) {
            return;
        }
        lastThornsTick = now;
        double reflected = damageAmount * value(BlueprintModule.THORNS, "reflectRatio");
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "thorns"), towerEntity, (float) value(BlueprintModule.THORNS, "radius"),
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE));
        TowerAreaDamage.apply(this, towerEntity, request, monster -> reflected, false, (monster, damage, killed) -> {
        }, primaryDamageType());
    }

    // ------------------------------------------------------------------ periodic

    @Override
    protected boolean execute(PlayerLane lane) {
        boolean periodic = level(BlueprintModule.REGEN) > 0 || level(BlueprintModule.HEAL_AURA) > 0
                || level(BlueprintModule.HASTE_AURA) > 0;
        if (!periodic) {
            return super.execute(lane);
        }
        SemionTowerEntity entity = runtimeEntity(lane).orElse(null);
        if (entity == null) {
            return false;
        }
        if (level(BlueprintModule.REGEN) > 0 && health() < currentMaxHealth()) {
            healTarget(entity, currentMaxHealth() * value(BlueprintModule.REGEN, "maxHealthPerSecond") * PULSE_TICKS / 20.0);
        }
        if (pulseCount++ % AURA_EVERY_PULSES == 0) {
            if (level(BlueprintModule.HEAL_AURA) > 0) {
                healAura(entity);
            }
            if (level(BlueprintModule.HASTE_AURA) > 0) {
                hasteAura(entity);
            }
        }
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return PULSE_TICKS;
    }

    private void healAura(SemionTowerEntity source) {
        double amount = currentMaxHealth() * value(BlueprintModule.HEAL_AURA, "maxHealthRatio");
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "heal_aura"), source, value(BlueprintModule.HEAL_AURA, "radius"),
                TowerAreaTargetMode.REGISTERED, AreaVfxSpec.onChange(AreaVfxStyles.BUFF));
        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            Tower ally = target.tower();
            SemionTowerEntity allyEntity = target.entity().orElse(null);
            if (allyEntity == null || ally.health() >= ally.currentMaxHealth() || !healTarget(allyEntity, amount)) {
                return AreaEffectOutcome.UNCHANGED;
            }
            allyEntity.playHealingAnimation();
            return AreaEffectOutcome.APPLIED;
        });
    }

    private void hasteAura(SemionTowerEntity source) {
        double bonus = value(BlueprintModule.HASTE_AURA, "attackSpeedBonus");
        int duration = PULSE_TICKS * AURA_EVERY_PULSES + PULSE_TICKS;
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "haste_aura"), source, value(BlueprintModule.HASTE_AURA, "radius"),
                TowerAreaTargetMode.REGISTERED, AreaVfxSpec.onChange(AreaVfxStyles.BUFF));
        SemionTdApi.areaEffects().applyToTowers(request, target -> target.entity()
                .filter(allyEntity -> allyEntity.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                        source("haste_aura"), bonus, duration))
                .map(ignored -> AreaEffectOutcome.APPLIED)
                .orElse(AreaEffectOutcome.UNCHANGED));
    }

    @Override
    public List<String> runtimeDetailLines() {
        BlueprintStats stats = blueprintStats();
        List<String> lines = new ArrayList<>();
        lines.add("대상 우선도: " + stats.targetPriority().displayName());
        stats.modules().forEach((module, moduleLevel) -> lines.add(module.displayName() + " " + moduleLevel + "단계"));
        return lines;
    }
}
