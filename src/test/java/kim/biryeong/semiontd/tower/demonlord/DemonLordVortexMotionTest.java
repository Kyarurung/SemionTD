package kim.biryeong.semiontd.tower.demonlord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class DemonLordVortexMotionTest {
    @Test
    void normalPullRetainsItsDistanceProfileAndVerticalVelocity() {
        Vec3 offset = new Vec3(3.0, 0.0, 4.0);
        Vec3 expected = offset.normalize().scale(0.16);
        Vec3 actual = DemonLordVortexMotion.velocity(offset, -0.2, 5.0, 0.16, 1.0);
        assertEquals(expected.x, actual.x, 1.0e-12);
        assertEquals(expected.z, actual.z, 1.0e-12);
        assertEquals(-0.2, actual.y);
        Vec3 inner = DemonLordVortexMotion.velocity(new Vec3(2.5, 0.0, 0.0), 0.3, 5.0, 0.16, 1.0);
        assertEquals(0.16 * (0.45 + 0.55 * 0.5), inner.x, 1.0e-12);
        assertEquals(0.3, inner.y);
    }

    @Test
    void doubledPullMatchesTwoNormalTickDisplacementsWithoutStackingOnRepeatedUpdates() {
        Vec3 offset = new Vec3(-3.0, 0.0, 4.0);
        Vec3 normal = DemonLordVortexMotion.velocity(offset, 0.25, 5.0, 0.16, 1.0);
        Vec3 accelerated = DemonLordVortexMotion.velocity(offset, 0.25, 5.0, 0.16, 2.0);
        assertEquals(normal.x * 2.0, accelerated.x, 1.0e-12);
        assertEquals(normal.z * 2.0, accelerated.z, 1.0e-12);
        assertEquals(normal.y, accelerated.y);
        assertEquals(accelerated, DemonLordVortexMotion.velocity(offset, accelerated.y, 5.0, 0.16, 2.0));
    }

    @Test
    void acceleratedPullStopsAtTheCentreInsteadOfCrossingIt() {
        Vec3 offset = new Vec3(0.48, 0.0, 0.64);
        Vec3 actual = DemonLordVortexMotion.velocity(offset, -0.6, 1.0, 1.0, 5.0);
        assertEquals(offset.x, actual.x, 1.0e-12);
        assertEquals(offset.z, actual.z, 1.0e-12);
        assertTrue(Math.hypot(actual.x, actual.z) <= offset.length() + 1.0e-12);
        assertEquals(-0.6, actual.y);
    }

    @Test
    void centreDeadZoneRetainsOnlyVerticalMotionAtEveryRate() {
        for (double multiplier : new double[] {1.0, 2.0, 5.0}) {
            assertEquals(new Vec3(0.0, 0.4, 0.0),
                    DemonLordVortexMotion.velocity(new Vec3(0.59, 10.0, 0.0), 0.4, 5.0, 0.16, multiplier));
            assertEquals(new Vec3(0.0, -0.4, 0.0),
                    DemonLordVortexMotion.velocity(Vec3.ZERO, -0.4, 5.0, 0.16, multiplier));
        }
    }

    @Test
    void normalConfiguredPullIsNotClampedByTheAccelerationCorrection() {
        Vec3 actual = DemonLordVortexMotion.velocity(new Vec3(0.8, 0.0, 0.0), 0.1, 1.0, 2.0, 1.0);
        assertEquals(2.0 * (0.45 + 0.55 * 0.8), actual.x, 1.0e-12);
        assertEquals(0.1, actual.y);
    }
}
