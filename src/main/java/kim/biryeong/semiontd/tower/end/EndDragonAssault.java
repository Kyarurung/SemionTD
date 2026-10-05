package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.LineTargets;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class EndDragonAssault {
    enum Phase { READY, REARWARD, CHARGING, RUSHING, ASCENDING, BREATHING, RETURNING, SPENT }

    private Phase phase = Phase.READY;
    private SemionTowerEntity source;
    private boolean previousNoAi;
    private Vec3 rear;
    private Vec3 front;
    private double groundY;
    private int chargeTicks;
    private final Set<UUID> rushHits = new HashSet<>();
    private final Set<UUID> breathHits = new HashSet<>();
    private final List<Burn> burns = new ArrayList<>();

    void reset() {
        cancel();
        phase = Phase.READY;
        rushHits.clear();
        breathHits.clear();
        burns.forEach(burn -> clearBurnMark(burn.target));
        burns.clear();
        source = null;
    }

    void cancel() {
        if (controlsFlight() && source != null) {
            source.setNoAi(previousNoAi);
            source.setDeltaMovement(Vec3.ZERO);
        }
        if (phase != Phase.READY) {phase = Phase.SPENT;}
    }

    void copyUseFrom(EndDragonAssault previous) {
        if (previous.phase != Phase.READY) {phase = Phase.SPENT;}
    }

    boolean controlsFlight() {return phase != Phase.READY && phase != Phase.SPENT;}
    Phase phase() {return phase;}

    void tick(EndTower tower, PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || !AugmentCombat.allowsTriggers()) {return;}
        if (!tower.augmentSnapshot().has(EndAugments.ASSAULT) || tower.state() != EndTowerState.DRAGON) {
            cancel();
            return;
        }
        SemionTowerEntity current = tower.runtimeEntity(lane).orElse(null);
        if (current == null || !current.isAlive() || current.isRemoved()) {cancel();return;}
        if (SuccubusDreams.isAsleep(current)) {return;}
        if (phase == Phase.SPENT) {return;}
        if (phase == Phase.READY) {
            source = current;
            previousNoAi = source.isNoAi();
            var layout = lane.laneLayout();
            groundY = layout.spawn().y;
            Vec3 back = layout.personalWaypoints().isEmpty() ? layout.bossPosition() : layout.personalWaypoints().getLast();
            rear = new Vec3(back.x, groundY + 1, back.z);
            front = new Vec3(layout.spawn().x, groundY + 1, layout.spawn().z);
            chargeTicks = 0;
            phase = Phase.REARWARD;
        }
        if (current != source) {cancel();return;}
        source.setNoAi(true);
        source.getNavigation().stop();
        source.setDeltaMovement(Vec3.ZERO);
        switch (phase) {
            case REARWARD -> {
                if (move(rear, .6)) {phase = Phase.CHARGING;}
            }
            case CHARGING -> {
                source.faceDragonPosition(front.add(0, source.getEyeHeight(), 0));
                EndVfx.assaultCharge(lane.arenaWorld(), source.position(),
                        chargeTicks / parameter(tower, "chargeTicks", 60), chargeTicks);
                if (++chargeTicks >= parameter(tower, "chargeTicks", 60)) {phase = Phase.RUSHING;}
            }
            case RUSHING -> {
                Vec3 from = source.position();
                boolean arrived = move(front, 1.5);
                rush(tower, lane, from, source.position());
                if (arrived) {phase = Phase.ASCENDING;}
            }
            case ASCENDING -> {
                if (move(high(tower, rear), .6)) {phase = Phase.BREATHING;}
            }
            case BREATHING -> {
                Vec3 from = source.position();
                boolean arrived = move(high(tower, front), .45);
                breathe(tower, lane.arenaWorld(), from, source.position());
                if (arrived) {phase = Phase.RETURNING;}
            }
            case RETURNING -> {
                if (move(rear, .6)) {cancel();}
            }
            default -> {}
        }
    }

    private Vec3 high(EndTower tower, Vec3 point) {
        return new Vec3(point.x, groundY + parameter(tower, "flightHeight", 10), point.z);
    }

    private boolean move(Vec3 destination, double speed) {
        Vec3 offset = destination.subtract(source.position());
        double distance = offset.length();
        if (distance > 1.0e-6) {source.faceDragonPosition(destination.add(0, source.getEyeHeight(), 0));}
        source.setPos(distance <= speed ? destination : source.position().add(offset.scale(speed / distance)));
        return distance <= speed;
    }

    private MonsterAreaEffectRequest sweep(EndTower tower, Vec3 from, Vec3 to, Set<UUID> excluded, String effect) {
        Vec3 groundFrom = new Vec3(from.x, groundY + 1, from.z);
        Vec3 groundTo = new Vec3(to.x, groundY + 1, to.z);
        double halfWidth = parameter(tower, "width", 6) / 2;
        return new MonsterAreaEffectRequest(AreaEffectIds.tower(tower, effect), source,
                groundFrom.lerp(groundTo, .5), groundFrom.distanceTo(groundTo) / 2 + Math.hypot(halfWidth, 3),
                excluded, target -> source.isValidAttackTarget(target) && !target.isDominated()
                        && Math.abs(target.getY() - groundY) <= 3
                        && LineTargets.distanceToSegment(target.position(), groundFrom, groundTo) <= halfWidth, null);
    }

    private void rush(EndTower tower, PlayerLane lane, Vec3 from, Vec3 to) {
        TowerAreaDamage.applyResolved(tower, source, sweep(tower, from, to, rushHits, "dragon_assault_rush"),
                target -> currentDamage(tower, target, parameter(tower, "rushDamageRatio", 1.0)), true,
                (target, damage, killed) -> {
                    rushHits.add(target.getUUID());
                    if (damage > 0 && !killed && target.runtimeMonster().senderTeam().isPresent()) {
                        target.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1, (int) parameter(tower, "stunTicks", 200));
                        knockBack(lane, target, parameter(tower, "knockbackDistance", 20));
                    }
                }, DamageType.PHYSICAL);
    }

    static void knockBack(PlayerLane lane, SemionMonsterEntity target, double distance) {
        var monster = target.runtimeMonster();
        if (monster == null || monster.inFinalDefenseCombat()
                || monster.id().toLowerCase(java.util.Locale.ROOT).contains("boss")) {return;}
        var layout = lane.laneLayout();
        double length = layout.pathLength();
        if (length <= 0 || distance <= 0) {return;}
        double initialProgress = layout.progressAt(target.position());
        double progress = initialProgress;
        Vec3 initialPath = layout.positionAt(initialProgress);
        Vec3 origin = target.position();
        Vec3 destination = origin;
        var bounds = layout.laneArea();
        double available = Math.min(distance, initialProgress * length);
        for (double moved = Math.min(.25, available); moved > 0 && moved <= available; moved = Math.min(moved + .25, available)) {
            double candidateProgress = Math.max(0, initialProgress - moved / length);
            Vec3 path = layout.positionAt(candidateProgress);
            Vec3 candidate = origin.add(path.x - initialPath.x, 0, path.z - initialPath.z);
            var box = target.getBoundingBox().move(candidate.subtract(origin));
            if (box.minX < bounds.min().getX() || box.maxX > bounds.max().getX() + 1
                    || box.minZ < bounds.min().getZ() || box.maxZ > bounds.max().getZ() + 1
                    || !target.level().noCollision(target, box)) {break;}
            destination = candidate;
            progress = candidateProgress;
            if (moved >= available) {break;}
        }
        if (progress < initialProgress) {
            target.rewindLanePath(destination, progress);
        }
    }

    private void breathe(EndTower tower, ServerLevel level, Vec3 from, Vec3 to) {
        EndVfx.assaultBreath(level, source.position(), new Vec3(to.x, groundY + 1, to.z),
                parameter(tower, "width", 6));
        SemionTdApi.areaEffects().applyToMonsters(sweep(tower, from, to, breathHits, "dragon_assault_breath"), target -> {
            breathHits.add(target.getUUID());
            target.applyTimedEffect(TimedEffectType.MONSTER_IGNITED, AreaEffectIds.tower(tower, "dragon_assault_burn"),
                    1, (int) parameter(tower, "burnDurationTicks", 200));
            burns.add(new Burn(target, (int) parameter(tower, "burnDurationTicks", 200),
                    (int) parameter(tower, "burnIntervalTicks", 20)));
            return AreaEffectOutcome.APPLIED;
        });
    }

    void tickBurns(EndTower tower) {
        if (source == null || !AugmentCombat.allowsTriggers()) {return;}
        for (Iterator<Burn> iterator = burns.iterator(); iterator.hasNext();) {
            Burn burn = iterator.next();
            if (!burn.target.isAlive() || burn.target.isRemoved()) {clearBurnMark(burn.target);iterator.remove();continue;}
            burn.remainingTicks--;
            if (--burn.untilDamage <= 0) {
                double damage = currentDamage(tower, burn.target, parameter(tower, "burnDamageRatio", .25));
                var result = tower.damageResolvedTargetResult(source, burn.target, damage, DamageType.MAGIC);
                if (result.killed()) {tower.onKill(source, burn.target, damage);}
                if (source.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, burn.target.getX(), burn.target.getY() + 1,
                            burn.target.getZ(), 5, .3, .4, .3, .01);
                }
                burn.untilDamage = (int) parameter(tower, "burnIntervalTicks", 20);
            }
            if (burn.remainingTicks <= 0 || !burn.target.isAlive()) {clearBurnMark(burn.target);iterator.remove();}
        }
    }

    private void clearBurnMark(SemionMonsterEntity target) {
        if (source != null && source.runtimeTower() != null) {
            target.applyTimedEffect(TimedEffectType.MONSTER_IGNITED,
                    AreaEffectIds.tower(source.runtimeTower(), "dragon_assault_burn"), 0, 1);
        }
    }

    private double currentDamage(EndTower tower, SemionMonsterEntity target, double ratio) {
        return tower.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target) * ratio);
    }

    private static double parameter(EndTower tower, String name, double fallback) {
        return tower.augmentSnapshot().parameter(EndAugments.ASSAULT, name, fallback);
    }

    String detail() {
        return "차원의 수호자: 라운드당 1회 / " + switch (phase) {
            case READY -> "드래곤 진화 대기";
            case REARWARD -> "후방 이동";
            case CHARGING -> "돌진 준비";
            case RUSHING -> "돌진";
            case ASCENDING -> "후방 상승";
            case BREATHING -> "브레스";
            case RETURNING -> "복귀";
            case SPENT -> "사용 완료";
        };
    }

    private static final class Burn {
        private final SemionMonsterEntity target;
        private int remainingTicks;
        private int untilDamage;

        private Burn(SemionMonsterEntity target, int remainingTicks, int untilDamage) {
            this.target = target;
            this.remainingTicks = remainingTicks;
            this.untilDamage = untilDamage;
        }
    }
}
