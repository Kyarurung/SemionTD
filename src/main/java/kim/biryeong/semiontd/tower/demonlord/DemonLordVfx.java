package kim.biryeong.semiontd.tower.demonlord;

import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.vfx.DisplayEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * 마왕 스킬 연출을 띄우는 곳. 연출 모양은 {@link DemonLordDisplayVfx}가 짭니다.
 *
 * <p>예전에는 공용 범위 파티클(스플래시·펄스 등)을 뿌렸지만, 이제는 디스플레이 엔티티 연출만 씁니다.
 */
public final class DemonLordVfx {
    private DemonLordVfx() {
    }

    /** 땅 표면에서 이 거리 안에 있는 기준점은 표면에 붙입니다(살짝 떠 있거나 파묻힌 몸 아래에 까는 연출). */
    static final double GROUND_SNAP = 0.25;

    public static boolean play(PlayerLane lane, DisplayEffect effect, Vec3 origin) {
        ServerLevel level = lane == null ? null : lane.arenaWorld();
        return level != null && effect.spawn(level, onGround(level, origin));
    }

    /**
     * 기준점 바로 아래(또는 살짝 위) 블록의 충돌 윗면이 {@link #GROUND_SNAP} 안이면 그 높이로 맞춥니다. 바닥 문양은 기준점에서
     * {@link kim.biryeong.semiontd.vfx.DisplayShapes#GROUND_LIFT}만큼 떠서 깔리므로, 기준점이 땅과 맞아야 z-fighting이 나지 않습니다.
     * 공중이나 벽처럼 가까운 땅이 없으면 그대로 둡니다.
     */
    static Vec3 onGround(BlockGetter level, Vec3 origin) {
        BlockPos pos = BlockPos.containing(origin.x, origin.y + GROUND_SNAP, origin.z);
        for (int step = 0; step < 2; step++, pos = pos.below()) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            return Math.abs(top - origin.y) <= GROUND_SNAP ? new Vec3(origin.x, top, origin.z) : origin;
        }
        return origin;
    }

    public static boolean follow(DisplayEffect effect, Entity entity) {
        return entity != null && effect.follow(entity);
    }

    public static long seed(PlayerLane lane) {
        return lane == null || lane.arenaWorld() == null ? 0L : lane.arenaWorld().getGameTime();
    }

    /** 디버그 명령과 시험용: 기본 수치로 그 스킬 연출을 한 번 띄웁니다. */
    public static boolean showDebug(DemonLordSkillTower altar, PlayerLane lane, Vec3 center) {
        return altar != null && play(lane, preview(altar.skill(), seed(lane)), center);
    }

    /** 기본 수치(1티어 기본 설정)로 짠 연출. 앞은 +Z입니다. 미리보기 페이지도 이것을 씁니다. */
    public static DisplayEffect preview(DemonLordSkill skill, long seed) {
        return switch (skill) {
            case WAVE_OF_MALICE -> DemonLordDisplayVfx.waveOfMalice(0.0F, 6.0, 60.0, seed);
            case DEMON_WINGS -> DemonLordDisplayVfx.demonWings(0.0F, seed);
            case SKY_BREAKER -> DemonLordDisplayVfx.skyBreaker(new Vector3f(0.0F, 0.0F, 8.0F), 2.0, seed);
            case ARCANE_BOMBARDMENT -> DemonLordDisplayVfx.arcaneImpact(4.0, seed);
            case DEMON_BARRIER -> DemonLordDisplayVfx.demonBarrier(seed);
            case HELLFIRE_BRAND -> DemonLordDisplayVfx.hellfireBrand(3.5, 100, seed);
            case SOUL_DRAIN -> DemonLordDisplayVfx.soulDrain(0.0F, 7.0, seed);
            case ROAR_OF_DREAD -> DemonLordDisplayVfx.roarOfDread(5.0, seed);
            case GRIP_OF_DOOM -> DemonLordDisplayVfx.gripOfDoom(true, 4.0, seed);
            case HELL_GUILLOTINE -> DemonLordDisplayVfx.hellGuillotine(0.0F, 4.0, seed);
            case RIFT_CLEAVE -> DemonLordDisplayVfx.riftCleave(0.0F, 1.6, 1.8, 5, 3, 1.8, 2.5, seed);
            case SUMMON_FIEND -> DemonLordDisplayVfx.summonFiend(seed);
            case ABYSS_VORTEX -> DemonLordDisplayVfx.abyssVortex(5.0, 60, seed);
        };
    }
}
