package kim.biryeong.semiontd.entity.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class WorkerPhysics {
    private static final double EPSILON = 1.0e-7;

    private WorkerPhysics() {
    }

    public enum Mode {
        AIR, WATER, LAVA, GLIDING, NONE
    }

    public enum ColliderKind {
        BLOCK, ENTITY, BORDER
    }

    public record Vector(double x, double y, double z) {
        public static final Vector ZERO = new Vector(0.0, 0.0, 0.0);

        public Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        public Vector multiply(double xScale, double yScale, double zScale) {
            return new Vector(x * xScale, y * yScale, z * zScale);
        }

        public double lengthSquared() {
            return x * x + y * y + z * z;
        }

        public double horizontalLengthSquared() {
            return x * x + z * z;
        }
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box move(Vector movement) {
            return new Box(minX + movement.x, minY + movement.y, minZ + movement.z,
                    maxX + movement.x, maxY + movement.y, maxZ + movement.z);
        }

        public Box expand(Vector movement) {
            return new Box(minX + Math.min(0.0, movement.x), minY + Math.min(0.0, movement.y),
                    minZ + Math.min(0.0, movement.z), maxX + Math.max(0.0, movement.x),
                    maxY + Math.max(0.0, movement.y), maxZ + Math.max(0.0, movement.z));
        }

        public boolean intersects(Box other) {
            return maxX > other.minX + EPSILON && minX < other.maxX - EPSILON
                    && maxY > other.minY + EPSILON && minY < other.maxY - EPSILON
                    && maxZ > other.minZ + EPSILON && minZ < other.maxZ - EPSILON;
        }

        private double minimum(int axis) {
            return axis == 0 ? minX : axis == 1 ? minY : minZ;
        }

        private double maximum(int axis) {
            return axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
        }
    }

    public record Collider(ColliderKind kind, List<Box> boxes, List<Double> yCoordinates) {
        public Collider {
            boxes = List.copyOf(boxes);
            yCoordinates = List.copyOf(yCoordinates);
        }

        private boolean intersects(Box query) {
            return boxes.stream().anyMatch(query::intersects);
        }
    }

    public record Travel(Mode mode, Vector velocity, Vector input, float acceleration, float yawSine,
                         float yawCosine, boolean climbable, double gravity, Vector look,
                         float leanAngle, float leanSine) {
    }

    public record Input(Travel travel, Box box, boolean onGround, boolean noPhysics, float maxUpStep,
                        Vector stuckSpeedMultiplier, List<Collider> colliders) {
        public Input {
            colliders = List.copyOf(colliders);
        }
    }

    public record Result(Vector travelMovement, Vector collisionInput, Vector movement) {
    }

    public static Result advance(Input input) {
        Vector travelMovement = travelMovement(input.travel);
        Vector requested = !input.noPhysics && input.stuckSpeedMultiplier.lengthSquared() > EPSILON
                ? travelMovement.multiply(input.stuckSpeedMultiplier.x, input.stuckSpeedMultiplier.y,
                input.stuckSpeedMultiplier.z) : travelMovement;
        if (input.travel.mode == Mode.NONE) {
            return new Result(travelMovement, Vector.ZERO, Vector.ZERO);
        }
        Vector movement = input.noPhysics ? requested : collide(input, requested);
        return new Result(travelMovement, requested, movement);
    }

    public static Vector travelMovement(Travel travel) {
        if (travel.mode == Mode.NONE) {
            return Vector.ZERO;
        }
        if (travel.mode == Mode.GLIDING) {
            return glide(travel);
        }
        Vector movement = travel.velocity.add(relativeInput(travel));
        if (travel.mode == Mode.AIR && travel.climbable) {
            movement = new Vector(Math.clamp(movement.x, -0.15F, 0.15F),
                    Math.max(movement.y, -0.15F), Math.clamp(movement.z, -0.15F, 0.15F));
        }
        return movement;
    }

    private static Vector relativeInput(Travel travel) {
        double lengthSquared = travel.input.lengthSquared();
        if (lengthSquared < EPSILON) {
            return Vector.ZERO;
        }
        double scale = travel.acceleration / (lengthSquared > 1.0 ? Math.sqrt(lengthSquared) : 1.0);
        Vector input = travel.input.multiply(scale, scale, scale);
        return new Vector(input.x * travel.yawCosine - input.z * travel.yawSine, input.y,
                input.z * travel.yawCosine + input.x * travel.yawSine);
    }

    private static Vector glide(Travel travel) {
        Vector movement = travel.velocity;
        double lookLength = Math.sqrt(travel.look.horizontalLengthSquared());
        double movementLength = Math.sqrt(movement.horizontalLengthSquared());
        double cosine = Math.cos(travel.leanAngle);
        double lift = cosine * cosine;
        movement = movement.add(new Vector(0.0, travel.gravity * (-1.0 + lift * 0.75), 0.0));
        if (movement.y < 0.0 && lookLength > 0.0) {
            double convert = movement.y * -0.1 * lift;
            movement = movement.add(new Vector(travel.look.x * convert / lookLength, convert,
                    travel.look.z * convert / lookLength));
        }
        if (travel.leanAngle < 0.0F && lookLength > 0.0) {
            double convert = movementLength * -travel.leanSine * 0.04;
            movement = movement.add(new Vector(-travel.look.x * convert / lookLength, convert * 3.2,
                    -travel.look.z * convert / lookLength));
        }
        if (lookLength > 0.0) {
            movement = movement.add(new Vector((travel.look.x / lookLength * movementLength - movement.x) * 0.1,
                    0.0, (travel.look.z / lookLength * movementLength - movement.z) * 0.1));
        }
        return movement.multiply(0.99F, 0.98F, 0.99F);
    }

    private static Vector collide(Input input, Vector requested) {
        if (requested.lengthSquared() == 0.0) {
            return requested;
        }
        Vector movement = collideWithBoxes(requested, input.box,
                collidersFor(input.colliders, input.box.expand(requested)));
        boolean grounded = requested.y != movement.y && requested.y < 0.0;
        if (input.maxUpStep <= 0.0F || !(input.onGround || grounded)
                || requested.x == movement.x && requested.z == movement.z) {
            return movement;
        }
        Box groundedBox = grounded ? input.box.move(new Vector(0.0, movement.y, 0.0)) : input.box;
        Box stepQuery = groundedBox.expand(new Vector(requested.x, input.maxUpStep, requested.z));
        if (!grounded) {
            stepQuery = stepQuery.expand(new Vector(0.0, -1.0e-5F, 0.0));
        }
        List<Collider> colliders = collidersFor(input.colliders, stepQuery);
        TreeSet<Float> heights = new TreeSet<>();
        for (Collider collider : colliders) {
            for (double coordinate : collider.yCoordinates) {
                float height = (float) (coordinate - groundedBox.minY);
                if (height >= 0.0F && height <= input.maxUpStep && height != (float) movement.y) {
                    heights.add(height);
                }
            }
        }
        for (float height : heights) {
            Vector stepped = collideWithBoxes(new Vector(requested.x, height, requested.z), groundedBox, colliders);
            if (stepped.horizontalLengthSquared() > movement.horizontalLengthSquared()) {
                return new Vector(stepped.x, stepped.y - (input.box.minY - groundedBox.minY), stepped.z);
            }
        }
        return movement;
    }

    private static List<Collider> collidersFor(List<Collider> colliders, Box query) {
        List<Collider> selected = new ArrayList<>();
        for (Collider collider : colliders) {
            if (collider.kind != ColliderKind.BLOCK || collider.intersects(query)) {
                selected.add(collider);
            }
        }
        return selected;
    }

    private static Vector collideWithBoxes(Vector requested, Box box, List<Collider> colliders) {
        if (colliders.isEmpty()) {
            return requested;
        }
        boolean zFirst = Math.abs(requested.x) < Math.abs(requested.z);
        double y = clip(1, box, requested.y, colliders);
        box = box.move(new Vector(0.0, y, 0.0));
        double x;
        double z;
        if (zFirst) {
            z = clip(2, box, requested.z, colliders);
            box = box.move(new Vector(0.0, 0.0, z));
            x = clip(0, box, requested.x, colliders);
        } else {
            x = clip(0, box, requested.x, colliders);
            box = box.move(new Vector(x, 0.0, 0.0));
            z = clip(2, box, requested.z, colliders);
        }
        return new Vector(x, y, z);
    }

    private static double clip(int axis, Box moving, double distance, List<Collider> colliders) {
        if (Math.abs(distance) < EPSILON) {
            return 0.0;
        }
        int b = (axis + 1) % 3;
        int c = (axis + 2) % 3;
        for (Collider collider : colliders) {
            for (Box obstacle : collider.boxes) {
                if (moving.maximum(b) <= obstacle.minimum(b) + EPSILON
                        || moving.minimum(b) >= obstacle.maximum(b) - EPSILON
                        || moving.maximum(c) <= obstacle.minimum(c) + EPSILON
                        || moving.minimum(c) >= obstacle.maximum(c) - EPSILON) {
                    continue;
                }
                if (distance > 0.0) {
                    double gap = obstacle.minimum(axis) - moving.maximum(axis);
                    if (gap >= -EPSILON) {
                        distance = Math.min(distance, gap);
                    }
                } else {
                    double gap = obstacle.maximum(axis) - moving.minimum(axis);
                    if (gap <= EPSILON) {
                        distance = Math.max(distance, gap);
                    }
                }
            }
        }
        return distance;
    }
}
