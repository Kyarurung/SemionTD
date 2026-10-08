package kim.biryeong.semiontd.tower.area;

import net.minecraft.world.phys.Vec3;

public record TowerContactSweep(Vec3 start, Vec3 end, double radius) {
    public Vec3 center() {
        return start.add(end).scale(0.5);
    }

    public double searchRadius() {
        return start.distanceTo(end) * 0.5 + radius;
    }

    public boolean contains(Vec3 point) {
        Vec3 direction = end.subtract(start);
        double lengthSquared = direction.lengthSqr();
        double progress = lengthSquared <= 1.0e-12 ? 0.0
                : Math.clamp(point.subtract(start).dot(direction) / lengthSquared, 0.0, 1.0);
        return point.distanceToSqr(start.add(direction.scale(progress))) <= radius * radius;
    }
}
