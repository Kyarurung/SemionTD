package kim.biryeong.semiontd.tower.area;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 직선 범위 판정. 높이는 보지 않고 바닥 평면에서 선분까지의 거리로 셉니다(레인은 평평함). */
public final class LineTargets {
    private LineTargets() {
    }

    /** 점에서 선분까지의 수평 거리. */
    public static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 flatPoint = new Vec3(point.x, 0.0, point.z);
        Vec3 flatFrom = new Vec3(from.x, 0.0, from.z);
        Vec3 segment = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0e-6) {
            return flatPoint.distanceTo(flatFrom);
        }
        double t = Math.max(0.0, Math.min(1.0, flatPoint.subtract(flatFrom).dot(segment) / lengthSqr));
        return flatPoint.distanceTo(flatFrom.add(segment.scale(t)));
    }

    /**
     * 이 타워가 지키는 레인의 적 중 선분(폭 width) 위에 있는 적을 가까운 순서로. 지배당한 적은 뺍니다.
     *
     * @param extraFilter 더 거를 조건(예: 이미 맞은 대상 빼기)
     */
    public static List<SemionMonsterEntity> enemiesAlong(SemionTowerEntity source, Vec3 from, Vec3 to, double width,
            Predicate<SemionMonsterEntity> extraFilter) {
        double halfWidth = width / 2.0;
        AABB box = new AABB(from, to).inflate(halfWidth + 0.5, 2.0, halfWidth + 0.5);
        return source.level().getEntitiesOfClass(SemionMonsterEntity.class, box, monster -> monster.isAlive()
                        && !monster.isDominated()
                        && monster.runtimeMonster() != null
                        && source.defendsLane(monster.runtimeMonster().targetLaneId())
                        && distanceToSegment(monster.position(), from, to) <= halfWidth
                        && extraFilter.test(monster))
                .stream()
                .sorted(Comparator.comparingDouble(monster -> monster.position().distanceToSqr(from)))
                .toList();
    }
}
