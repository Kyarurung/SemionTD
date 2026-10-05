package kim.biryeong.semiontd.tower.end;

import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class EndVfx {
    private static final double SOURCE_HEIGHT = 2.25;
    private static final double TARGET_HEIGHT = 4.0;
    private static final AreaVfxSpec SPLASH = AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH);
    private static final AreaVfxSpec DRAGON_BREATH = AreaVfxSpec.onTrigger(AreaVfxStyles.DRAGON_BREATH);

    private EndVfx() {
    }

    static AreaVfxSpec attack(EndTowerState state, boolean splash) {
        if (state == EndTowerState.DRAGON) {return DRAGON_BREATH;}
        return splash ? SPLASH : null;
    }

    static void transfer(PlayerLane lane, Tower target, Tower source) {
        ServerLevel level = lane.arenaWorld();
        if (level == null) {return;}
        Vec3 targetPosition = particlePosition(target, TARGET_HEIGHT);
        Vec3 sourceOffset = particlePosition(source, SOURCE_HEIGHT).subtract(targetPosition);
        level.sendParticles(ParticleTypes.ENCHANT, targetPosition.x, targetPosition.y, targetPosition.z, 0, sourceOffset.x, sourceOffset.y, sourceOffset.z, 1.0);
    }

    static void assaultCharge(ServerLevel level, Vec3 center, double progress, int tick) {
        if (tick % 3 != 0) {return;}
        double radius = 2.5 * (1.0 - Math.min(1.0, progress));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1, center.z,
                8, radius, .6, radius, .08);
    }

    static void assaultBreath(ServerLevel level, Vec3 source, Vec3 ground, double width) {
        for (int step = 0; step <= 6; step++) {
            Vec3 point = source.lerp(ground, step / 6.0);
            level.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), point.x, point.y, point.z,
                    2, .25, .25, .25, .02);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ground.x, ground.y, ground.z,
                5, width / 3, .2, width / 3, .02);
    }

    private static Vec3 particlePosition(Tower tower, double height) {
        return new Vec3(tower.position().x() + 0.5, tower.position().y() + height, tower.position().z() + 0.5);
    }
}
