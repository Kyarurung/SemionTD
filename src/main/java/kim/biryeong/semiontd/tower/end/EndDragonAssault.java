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
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class EndDragonAssault {
    enum Phase { READY, CHARGING, RUSHING, EXITING, VANISHED, BREATHING, RETURNING, SPENT }

    private Phase phase = Phase.READY;
    private SemionTowerEntity source;
    private boolean previousNoAi;
    private boolean previousInvisible;
    private EndDragonAssaultGeometry geometry;
    private int sweepTicks;
    private static final int SWEEP_TICKS = 60;
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
            source.setInvisible(previousInvisible);
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
            previousInvisible = source.isInvisible();
            groundY = floorY(lane);
            geometry = EndDragonAssaultGeometry.from(lane.laneLayout(), groundY);
            chargeTicks = 0;
            phase = Phase.CHARGING;
            source.setNoAi(true);
            source.getNavigation().stop();
            source.setDeltaMovement(Vec3.ZERO);
            face(geometry.front().add(0, source.getEyeHeight(), 0));
            return;
        }
        if (current != source) {cancel();return;}
        source.setNoAi(true);
        source.getNavigation().stop();
        source.setDeltaMovement(Vec3.ZERO);
        switch (phase) {
            case CHARGING -> {
                face(geometry.front().add(0, source.getEyeHeight(), 0));
                EndVfx.assaultCharge(lane.arenaWorld(), source.position(),
                        chargeTicks / parameter(tower, "chargeTicks", 60), chargeTicks);
                if (++chargeTicks >= parameter(tower, "chargeTicks", 60)) {
                    source.setPos(geometry.rear());
                    face(geometry.front().add(0, source.getEyeHeight(), 0));
                    sweepTicks = 0;
                    phase = Phase.RUSHING;
                }
            }
            case RUSHING -> {
                double from = geometry.length() * sweepTicks / SWEEP_TICKS;
                double to = geometry.length() * ++sweepTicks / SWEEP_TICKS;
                source.setPos(geometry.point(to));
                face(source.position().add(geometry.direction()).add(0, source.getEyeHeight(), 0));
                rush(tower, lane, from, to);
                EndVfx.assaultWave(lane.arenaWorld(), geometry.point(to), geometry.direction(), geometry.width());
                if (sweepTicks == SWEEP_TICKS) {phase = Phase.EXITING;}
            }
            case EXITING -> {
                if (move(geometry.point(geometry.length() + 5), geometry.length() / SWEEP_TICKS)) {
                    source.setInvisible(true);
                    phase = Phase.VANISHED;
                }
            }
            case VANISHED -> {
                source.setPos(geometry.airborneRear(groundY, parameter(tower, "flightHeight", 10)));
                source.setInvisible(previousInvisible);
                face(geometry.rear().add(0, -1, 0));
                sweepTicks = 0;
                phase = Phase.BREATHING;
            }
            case BREATHING -> {
                double from = geometry.length() * sweepTicks / SWEEP_TICKS;
                double to = geometry.length() * ++sweepTicks / SWEEP_TICKS;
                Vec3 ground = geometry.point(to).add(0, -1, 0);
                face(ground);
                breathe(tower, lane.arenaWorld(), from, to);
                if (sweepTicks == SWEEP_TICKS) {phase = Phase.RETURNING;}
            }
            case RETURNING -> {
                source.setPos(geometry.rear());
                cancel();
            }
            default -> {}
        }
    }

    private static double floorY(PlayerLane lane) {
        BlockPos start = BlockPos.containing(lane.laneLayout().spawn());
        for (int depth = 0; depth <= 8; depth++) {
            BlockPos pos = start.below(depth);
            var shape = lane.arenaWorld().getBlockState(pos).getCollisionShape(lane.arenaWorld(), pos);
            if (!shape.isEmpty()) {return pos.getY() + shape.max(Direction.Axis.Y);}
        }
        return lane.laneLayout().spawn().y;
    }

    private void face(Vec3 target) {
        source.faceDragonPosition(new Vec3(2 * source.getX() - target.x, target.y, 2 * source.getZ() - target.z));
    }

    private boolean move(Vec3 destination, double speed) {
        Vec3 offset = destination.subtract(source.position());
        double distance = offset.length();
        if (distance > 1.0e-6) {face(destination.add(0, source.getEyeHeight(), 0));}
        source.setPos(distance <= speed ? destination : source.position().add(offset.scale(speed / distance)));
        return distance <= speed;
    }

    private MonsterAreaEffectRequest sweep(EndTower tower, double from, double to, Set<UUID> excluded, String effect) {
        Vec3 center = geometry.point((from + to) / 2);
        double radius = Math.sqrt(Math.pow((to - from) / 2, 2) + Math.pow(geometry.width() / 2, 2) + 16);
        return new MonsterAreaEffectRequest(AreaEffectIds.tower(tower, effect), source, center, radius,
                excluded, target -> source.isValidAttackTarget(target) && !target.isDominated()
                        && target.runtimeMonster().targetLaneId() == tower.laneId()
                        && Math.abs(target.getY() - groundY) <= 3
                        && geometry.swept(target.position(), from, to), null);
    }

    private void rush(EndTower tower, PlayerLane lane, double from, double to) {
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

    private void breathe(EndTower tower, ServerLevel level, double from, double to) {
        EndVfx.assaultBreath(level, source.position(), geometry.point(to).add(0, -1, 0),
                geometry.direction(), geometry.width());
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
            if (!burn.target.isAlive() || burn.target.isRemoved() || burn.target.isDominated()
                    || burn.target.runtimeMonster().targetTeam() != tower.teamId()
                    || burn.target.runtimeMonster().targetLaneId() != tower.laneId()) {clearBurnMark(burn.target);iterator.remove();continue;}
            burn.remainingTicks--;
            if (--burn.untilDamage <= 0) {
                double damage = currentDamage(tower, burn.target, parameter(tower, "burnDamageRatio", .25));
                var result = tower.damageResolvedTargetResult(source, burn.target, damage, DamageType.MAGIC);
                if (result.killed()) {tower.onKill(source, burn.target, damage);}
                if (source.level() instanceof ServerLevel level) {
                    level.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), burn.target.getX(), burn.target.getY() + 1,
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
            case CHARGING -> "돌진 준비";
            case RUSHING -> "돌진";
            case EXITING -> "전방 이탈";
            case VANISHED -> "후방 재등장";
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
