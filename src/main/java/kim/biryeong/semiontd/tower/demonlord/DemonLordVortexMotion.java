package kim.biryeong.semiontd.tower.demonlord;

import net.minecraft.world.phys.Vec3;

final class DemonLordVortexMotion {
    private DemonLordVortexMotion() {
    }

    static Vec3 velocity(Vec3 toCentre, double verticalVelocity, double radius, double pullStrength, double multiplier) {
        Vec3 horizontal = new Vec3(toCentre.x, 0.0, toCentre.z);
        double distance = horizontal.length();
        if (distance < 0.6) {
            return new Vec3(0.0, verticalVelocity, 0.0);
        }
        double edge = Math.min(1.0, distance / radius);
        double speed = pullStrength * (0.45 + 0.55 * edge);
        if (multiplier > 1.0) {
            speed = Math.min(distance, speed * multiplier);
        }
        Vec3 pull = horizontal.normalize().scale(speed);
        return new Vec3(pull.x, verticalVelocity, pull.z);
    }
}
