package kim.biryeong.semiontd.tower.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class TowerContactSweepTest {
    @Test
    void detectsContactBetweenSamplesWithoutExpandingThePathWidth() {
        var sweep = new TowerContactSweep(Vec3.ZERO, new Vec3(4, 0, 0), 0.5);
        assertTrue(sweep.contains(new Vec3(2, 0, 0)));
        assertTrue(sweep.contains(new Vec3(2, 0.5, 0)));
        assertFalse(sweep.contains(new Vec3(2, 0.51, 0)));
        assertFalse(sweep.contains(new Vec3(-0.51, 0, 0)));
        assertFalse(sweep.contains(new Vec3(4.51, 0, 0)));
        assertEquals(new Vec3(2, 0, 0), sweep.center());
        assertEquals(2.5, sweep.searchRadius());
    }

    @Test
    void stationaryContactRetainsTheOriginalRadius() {
        var center = new Vec3(3, 4, 5);
        var sweep = new TowerContactSweep(center, center, 2);
        assertTrue(sweep.contains(center.add(0, 2, 0)));
        assertFalse(sweep.contains(center.add(0, 2.01, 0)));
        assertEquals(center, sweep.center());
        assertEquals(2, sweep.searchRadius());
    }

    @Test
    void followingCornerSegmentsDoesNotHitTheDiagonalShortcut() {
        var turn = new Vec3(4, 0, 0);
        var first = new TowerContactSweep(Vec3.ZERO, turn, 0.5);
        var second = new TowerContactSweep(turn, new Vec3(4, 0, 4), 0.5);
        assertTrue(first.contains(new Vec3(2, 0, 0)));
        assertTrue(second.contains(new Vec3(4, 0, 2)));
        assertFalse(first.contains(new Vec3(2, 0, 2)));
        assertFalse(second.contains(new Vec3(2, 0, 2)));
    }

    @Test
    void partitionedMovementCoversTheSameStraightContactPath() {
        var complete = new TowerContactSweep(Vec3.ZERO, new Vec3(5, 0, 0), 0.25);
        for (int sample = -5; sample <= 55; sample++) {
            Vec3 point = new Vec3(sample / 10.0, 0.2, 0);
            boolean partitioned = false;
            for (int step = 0; step < 5; step++) {
                partitioned |= new TowerContactSweep(new Vec3(step, 0, 0), new Vec3(step + 1, 0, 0), 0.25)
                        .contains(point);
            }
            assertEquals(complete.contains(point), partitioned);
        }
    }
}
