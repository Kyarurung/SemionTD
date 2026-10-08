package kim.biryeong.semiontd.entity.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WorkerPhysicsTest {
    private static final WorkerPhysics.Box BODY = new WorkerPhysics.Box(0, 1, 0, 1, 3, 1);

    @Test
    void copiedGeometryCannotChangeAfterWorkerSubmission() {
        List<WorkerPhysics.Box> boxes = new ArrayList<>(List.of(new WorkerPhysics.Box(0, 0, 0, 1, 1, 1)));
        List<Double> heights = new ArrayList<>(List.of(0.0, 1.0));
        WorkerPhysics.Collider collider = new WorkerPhysics.Collider(WorkerPhysics.ColliderKind.BLOCK, boxes, heights);
        List<WorkerPhysics.Collider> colliders = new ArrayList<>(List.of(collider));
        WorkerPhysics.Input input = input(new WorkerPhysics.Vector(0, -0.2, 0), 0.0F, colliders);
        boxes.clear();
        heights.clear();
        colliders.clear();
        assertEquals(WorkerPhysics.Vector.ZERO, WorkerPhysics.advance(input).movement());
        assertThrows(UnsupportedOperationException.class, () -> input.colliders().clear());
    }

    @Test
    void fallingAndWallContactClipWithoutDeletingTangentialMovement() {
        List<WorkerPhysics.Collider> colliders = List.of(
                shape(new WorkerPhysics.Box(-10, 0, -10, 10, 1, 10)),
                shape(new WorkerPhysics.Box(1.25, 1, -10, 2, 4, 10)));
        WorkerPhysics.Result result = WorkerPhysics.advance(input(new WorkerPhysics.Vector(0.5, -0.1, 0.3), 0, colliders));
        assertEquals(new WorkerPhysics.Vector(0.25, 0.0, 0.3), result.movement());
    }

    @Test
    void nativeStepCandidatesClimbSlabButRejectFullBlock() {
        WorkerPhysics.Vector movement = new WorkerPhysics.Vector(0.5, -0.1, 0);
        WorkerPhysics.Collider ground = shape(new WorkerPhysics.Box(-10, 0, -10, 10, 1, 10));
        WorkerPhysics.Result slab = WorkerPhysics.advance(input(movement, 0.6F,
                List.of(ground, shape(new WorkerPhysics.Box(1, 1, -10, 2, 1.5, 10)))));
        assertEquals(new WorkerPhysics.Vector(0.5, 0.5, 0), slab.movement());
        WorkerPhysics.Result wall = WorkerPhysics.advance(input(movement, 0.6F,
                List.of(ground, shape(new WorkerPhysics.Box(1, 1, -10, 2, 2, 10)))));
        assertEquals(WorkerPhysics.Vector.ZERO, wall.movement());
    }

    @Test
    void climbableClampsVelocityBeforeCollision() {
        WorkerPhysics.Travel travel = new WorkerPhysics.Travel(WorkerPhysics.Mode.AIR,
                new WorkerPhysics.Vector(1, -1, -1), WorkerPhysics.Vector.ZERO, 0, 0, 1, true,
                0.08, WorkerPhysics.Vector.ZERO, 0, 0);
        WorkerPhysics.Input input = new WorkerPhysics.Input(travel, BODY, false, false, 0,
                WorkerPhysics.Vector.ZERO, List.of());
        assertEquals(new WorkerPhysics.Vector(0.15F, -0.15F, -0.15F), WorkerPhysics.advance(input).movement());
    }

    @Test
    void stuckMultiplierAffectsCollisionInputButNoPhysicsBypassesIt() {
        WorkerPhysics.Input input = input(new WorkerPhysics.Vector(1, -1, 1), 0, List.of());
        WorkerPhysics.Vector stuck = new WorkerPhysics.Vector(0.25, 0.05, 0.25);
        WorkerPhysics.Input trapped = new WorkerPhysics.Input(input.travel(), BODY, false, false, 0, stuck, List.of());
        assertEquals(new WorkerPhysics.Vector(0.25, -0.05, 0.25), WorkerPhysics.advance(trapped).movement());
        WorkerPhysics.Input ghost = new WorkerPhysics.Input(input.travel(), BODY, false, true, 0, stuck, List.of());
        assertEquals(new WorkerPhysics.Vector(1, -1, 1), WorkerPhysics.advance(ghost).movement());
    }

    @Test
    void relativeInputNormalizesAndUsesCopiedNativeYawValues() {
        WorkerPhysics.Travel travel = new WorkerPhysics.Travel(WorkerPhysics.Mode.WATER,
                WorkerPhysics.Vector.ZERO, new WorkerPhysics.Vector(0, 0, 2), 0.02F, 1, 0, false,
                0.08, WorkerPhysics.Vector.ZERO, 0, 0);
        assertEquals(new WorkerPhysics.Vector(-0.02F, 0, 0), WorkerPhysics.travelMovement(travel));
    }

    private static WorkerPhysics.Input input(WorkerPhysics.Vector velocity, float stepHeight,
                                             List<WorkerPhysics.Collider> colliders) {
        WorkerPhysics.Travel travel = new WorkerPhysics.Travel(WorkerPhysics.Mode.AIR, velocity,
                WorkerPhysics.Vector.ZERO, 0, 0, 1, false, 0.08, WorkerPhysics.Vector.ZERO, 0, 0);
        return new WorkerPhysics.Input(travel, BODY, false, false, stepHeight, WorkerPhysics.Vector.ZERO, colliders);
    }

    private static WorkerPhysics.Collider shape(WorkerPhysics.Box box) {
        return new WorkerPhysics.Collider(WorkerPhysics.ColliderKind.BLOCK, List.of(box), List.of(box.minY(), box.maxY()));
    }
}
