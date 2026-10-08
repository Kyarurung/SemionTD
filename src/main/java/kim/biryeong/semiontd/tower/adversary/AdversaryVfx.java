package kim.biryeong.semiontd.tower.adversary;

import kim.biryeong.semiontd.game.CombatSpeedRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class AdversaryVfx {
    private static final DustParticleOptions FOX_ORANGE = dust(0xF28C28, 1.0F);
    private static final DustParticleOptions FOX_GOLD = dust(0xFFD166, 0.9F);
    private static final DustParticleOptions WIND_CYAN = dust(0x8FE9FF, 0.85F);
    private static final DustParticleOptions FIREWORK_RED = dust(0xFF4D6D, 0.9F);
    private static final DustParticleOptions OMINOUS_PURPLE = dust(0x8B5CF6, 1.0F);
    private static final DustParticleOptions SCULK_DARK = dust(0x0B4F57, 1.0F);
    private static final DustParticleOptions SCULK_BRIGHT = dust(0x63E6E2, 0.85F);

    private AdversaryVfx() {
    }

    static void showSecondaryAttack(
            FoxForm form,
            SemionTowerEntity source,
            SemionMonsterEntity target
    ) {
        if (form == null || source == null || target == null
                || !(source.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 start = towerCenter(source);
        Vec3 end = targetCenter(target);
        switch (form) {
            case BREEZE -> {
                beam(level, start, end, WIND_CYAN, ParticleTypes.ELECTRIC_SPARK, 14);
                level.sendParticles(ParticleTypes.CLOUD, end.x, end.y, end.z, 2, 0.16, 0.1, 0.16, 0.015);
            }
            case GOLDEN_FANG -> {
                beam(level, start, end, FOX_GOLD, ParticleTypes.CRIT, 12);
                level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 5, 0.2, 0.2, 0.2, 0.08);
            }
            case SHIELD_BEARER -> {
                ring(level, FOX_ORANGE, start, 0.55, 16);
                beam(level, start, end, FOX_ORANGE, FOX_GOLD, 12);
            }
            case FIREWORK_PIERCER -> {
                beam(level, start, end, FIREWORK_RED, FOX_GOLD, 16);
                level.sendParticles(ParticleTypes.FIREWORK, end.x, end.y, end.z, 3, 0.16, 0.16, 0.16, 0.04);
            }
            case MACE_EXECUTIONER -> showMaceImpact(level, end);
            default -> {
            }
        }
    }

    static void showMaceFocus(
            SemionTowerEntity source,
            SemionMonsterEntity target,
            int remainingTicks,
            int totalTicks
    ) {
        if (source == null || target == null || !(source.level() instanceof ServerLevel level)) {
            return;
        }
        double progress = 1.0 - Math.max(0.0, Math.min(1.0, remainingTicks / (double) Math.max(1, totalTicks)));
        Vec3 center = targetCenter(target).add(0.0, -Math.max(0.25, target.getBbHeight() * 0.45), 0.0);
        ring(level, FOX_ORANGE, center, 1.15 - progress * 0.5, 20);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.15, center.z,
                3, 0.18, 0.08, 0.18, 0.01);
    }

    static void showSculkWarning(ServerLevel level, Vec3 center, double radius, int remainingTicks, int totalTicks) {
        if (level == null || center == null) {
            return;
        }
        double progress = 1.0 - Math.max(0.0, Math.min(1.0, remainingTicks / (double) Math.max(1, totalTicks)));
        Vec3 ground = center.add(0.0, 0.08, 0.0);
        ring(level, SCULK_DARK, ground, Math.max(0.6, radius), 28);
        ring(level, SCULK_BRIGHT, ground.add(0.0, 0.08, 0.0), Math.max(0.35, radius * (0.7 - progress * 0.35)), 18);
    }

    static void showSculkDetonation(ServerLevel level, Vec3 center, double radius) {
        if (level == null || center == null) {
            return;
        }
        Vec3 ground = center.add(0.0, 0.12, 0.0);
        ring(level, SCULK_BRIGHT, ground, Math.max(0.6, radius * 0.45), 20);
        ring(level, SCULK_DARK, ground, Math.max(0.8, radius), 32);
        level.sendParticles(ParticleTypes.SCULK_SOUL, ground.x, ground.y + 0.2, ground.z,
                12, Math.min(1.0, radius * 0.25), 0.25, Math.min(1.0, radius * 0.25), 0.03);
        level.sendParticles(ParticleTypes.SONIC_BOOM, ground.x, ground.y + 0.35, ground.z, 1, 0, 0, 0, 0);
    }

    static void showSupportPulse(SemionTowerEntity source, FoxForm form) {
        if (source == null || form == null || !(source.level() instanceof ServerLevel level)
                || Math.floorMod(CombatSpeedRuntime.gameTime(level) + source.getId(), 40) != 0) {
            return;
        }
        Vec3 center = towerCenter(source).add(0.0, -0.35, 0.0);
        switch (form) {
            case BELL_KEEPER -> {
                ring(level, FOX_GOLD, center, 1.25, 22);
                level.sendParticles(ParticleTypes.NOTE, center.x, center.y + 0.65, center.z,
                        3, 0.35, 0.2, 0.35, 0.0);
            }
            case BEACON_KEEPER -> {
                ring(level, FOX_GOLD, center, 1.55, 26);
                vertical(level, center, ParticleTypes.END_ROD, 1.6, 10);
            }
            case OMINOUS_HEXER -> {
                ring(level, OMINOUS_PURPLE, center, 1.55, 26);
                level.sendParticles(ParticleTypes.WITCH, center.x, center.y + 0.55, center.z,
                        5, 0.4, 0.25, 0.4, 0.01);
            }
            default -> {
            }
        }
    }

    static void showFoxHealing(SemionTowerEntity source, SemionTowerEntity target) {
        if (source == null || target == null || !(source.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 start = towerCenter(source);
        Vec3 end = towerCenter(target);
        beam(level, start, end, FOX_GOLD, ParticleTypes.END_ROD, 14);
        level.sendParticles(ParticleTypes.HEART, end.x, end.y + 0.2, end.z,
                3, 0.18, 0.16, 0.18, 0.0);
    }

    public static void showDebug(ServerPlayer player, DebugKind kind) {
        if (player == null || kind == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 forward = horizontalLook(player);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 source = player.position().add(forward.scale(2.5)).add(0.0, 1.0, 0.0);
        Vec3 target = player.position().add(forward.scale(6.0)).add(0.0, 1.0, 0.0);
        switch (kind) {
            case BREEZE -> {
                beam(level, source, target, WIND_CYAN, ParticleTypes.ELECTRIC_SPARK, 18);
                level.sendParticles(ParticleTypes.CLOUD, target.x, target.y, target.z,
                        3, 0.22, 0.15, 0.22, 0.015);
            }
            case GOLDEN -> {
                beam(level, source, target, FOX_GOLD, ParticleTypes.CRIT, 16);
                level.sendParticles(ParticleTypes.CRIT, target.x, target.y, target.z,
                        8, 0.25, 0.25, 0.25, 0.08);
            }
            case SHIELD -> {
                ring(level, FOX_ORANGE, source, 0.75, 20);
                beam(level, source, target, FOX_ORANGE, FOX_GOLD, 14);
            }
            case SUPPORT -> {
                Vec3 bell = target.add(right.scale(-2.0));
                Vec3 beacon = target;
                Vec3 ominous = target.add(right.scale(2.0));
                ring(level, FOX_GOLD, bell, 0.9, 18);
                level.sendParticles(ParticleTypes.NOTE, bell.x, bell.y + 0.5, bell.z, 4, 0.2, 0.15, 0.2, 0.0);
                ring(level, FOX_GOLD, beacon, 1.0, 20);
                vertical(level, beacon, ParticleTypes.END_ROD, 1.6, 10);
                ring(level, OMINOUS_PURPLE, ominous, 1.0, 20);
                level.sendParticles(ParticleTypes.WITCH, ominous.x, ominous.y + 0.5, ominous.z,
                        6, 0.25, 0.18, 0.25, 0.01);
            }
            case FIREWORK -> {
                beam(level, source, target, FIREWORK_RED, FOX_GOLD, 20);
                level.sendParticles(ParticleTypes.FIREWORK, target.x, target.y, target.z,
                        8, 0.3, 0.3, 0.3, 0.05);
            }
            case MACE -> {
                ring(level, FOX_ORANGE, target.add(0.0, -0.85, 0.0), 0.75, 22);
                showMaceImpact(level, target);
            }
            case SCULK -> {
                showSculkWarning(level, target.add(0.0, -0.85, 0.0), 2.4, 10, 40);
                showSculkDetonation(level, target.add(0.0, -0.85, 0.0), 2.4);
            }
        }
    }

    private static void showMaceImpact(ServerLevel level, Vec3 center) {
        ring(level, FOX_ORANGE, center.add(0.0, -0.55, 0.0), 1.1, 24);
        ring(level, FOX_GOLD, center.add(0.0, -0.45, 0.0), 0.55, 16);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z,
                10, 0.3, 0.18, 0.3, 0.12);
    }

    private static void beam(
            ServerLevel level,
            Vec3 source,
            Vec3 target,
            ParticleOptions primary,
            ParticleOptions accent,
            int points
    ) {
        Vec3 offset = target.subtract(source);
        for (int index = 0; index <= points; index++) {
            Vec3 point = source.add(offset.scale(index / (double) points));
            ParticleOptions particle = index % 4 == 0 ? accent : primary;
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    private static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            level.sendParticles(particle,
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    private static void vertical(ServerLevel level, Vec3 center, ParticleOptions particle, double height, int points) {
        for (int index = 0; index <= points; index++) {
            Vec3 point = center.add(0.0, height * index / points, 0.0);
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    private static Vec3 towerCenter(SemionTowerEntity tower) {
        return tower.position().add(0.0, Math.max(0.4, tower.getBbHeight() * 0.55), 0.0);
    }

    private static Vec3 targetCenter(SemionMonsterEntity target) {
        return target.position().add(0.0, Math.max(0.4, target.getBbHeight() * 0.5), 0.0);
    }

    private static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        return horizontal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
    }

    private static DustParticleOptions dust(int color, float scale) {
        return new DustParticleOptions(color, scale);
    }

    public enum DebugKind {
        BREEZE,
        GOLDEN,
        SHIELD,
        SUPPORT,
        FIREWORK,
        MACE,
        SCULK
    }
}
