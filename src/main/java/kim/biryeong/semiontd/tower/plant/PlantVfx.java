package kim.biryeong.semiontd.tower.plant;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 식물 연출 디버그. 연출 자체는 {@link PlantDisplayVfx}가 짭니다. */
public final class PlantVfx {
    private PlantVfx() {
    }

    /** 물병 식물 포격을 타워에서 플레이어가 보는 방향 8칸 앞으로 한 번 띄웁니다. */
    public static void showDebug(SemionTowerEntity tower, ServerPlayer player) {
        if (!(tower.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 horizontal = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        horizontal = horizontal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
        Vec3 target = player.position().add(horizontal.scale(8.0));
        String id = PlantTowers.T3_PODZOL_PITCHER_TOWER.id();
        double radius = TowerBalanceRuntime.ability(id, "splashRadius", 4.0);
        double arc = TowerBalanceRuntime.ability(id, "lobArcHeight", 5.0);
        Vec3 from = tower.position();
        Vector3f end = new Vector3f((float) (target.x - from.x), (float) (tower.getY() - from.y), (float) (target.z - from.z));
        PlantDisplayVfx.play(level, PlantDisplayVfx.pitcherLob(end, arc, radius, true, PlantDisplayVfx.seed(level)), from);
    }
}
