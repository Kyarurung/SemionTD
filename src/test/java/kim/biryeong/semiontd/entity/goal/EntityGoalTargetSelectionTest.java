package kim.biryeong.semiontd.entity.goal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class EntityGoalTargetSelectionTest {
    private record Target(int identity, double priority, double distance) {}

    @Test
    void boundedSelectionMatchesStableFullOrderingAcrossSizesAndTies() {
        Random random = new Random(2603);
        List<Comparator<Target>> orders = List.of(
                Comparator.comparingDouble(Target::distance),
                Comparator.comparingDouble(Target::priority).reversed().thenComparingDouble(Target::distance),
                Comparator.comparingDouble(Target::priority).thenComparingDouble(Target::distance)
                        .thenComparingInt(Target::identity));
        for (int size : new int[] {0, 1, 6, 7, 8, 9, 32, 128, 512, 4096}) {
            List<Target> input = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                input.add(new Target(i, random.nextInt(7), random.nextInt(13)));
            }
            Collections.shuffle(input, random);
            List<Target> unchanged = List.copyOf(input);
            for (Comparator<Target> order : orders) {
                for (int limit : new int[] {0, 1, 3, 6, 8, size, size + 1}) {
                    List<Target> expected = input.stream().sorted(order).limit(limit).toList();
                    assertEquals(expected, EntityGoalTargetSelection.first(input, order, limit),
                            "size=" + size + " limit=" + limit);
                    assertEquals(unchanged, input, "Selection must not reorder the query result.");
                }
            }
        }
    }

    @Test
    void equalPriorityKeepsEncounterOrderAndRepeatedReferences() {
        Target repeated = new Target(1, 0, 0);
        List<Target> input = List.of(repeated, new Target(2, 0, 0), repeated, new Target(3, 0, 0));
        List<Target> actual = EntityGoalTargetSelection.first(input, Comparator.comparingDouble(Target::distance), 3);
        assertEquals(input.subList(0, 3), actual);
        assertSame(repeated, actual.getFirst());
        assertSame(repeated, actual.getLast());
    }

    @Test
    void floatingPointOrderMatchesJavaComparatorIncludingSignedZeroAndNan() {
        List<Double> input = List.of(Double.NaN, 0.0, -0.0, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 3.0, Double.NaN, -1.0);
        for (int limit = 0; limit <= input.size(); limit++) {
            assertEquals(input.stream().sorted(Double::compare).limit(limit).toList(),
                    EntityGoalTargetSelection.first(input, Double::compare, limit));
        }
    }

    @Test
    void eachInvocationObservesCurrentPriorityAndDoesNotCacheTargets() {
        List<Integer> input = List.of(0, 1, 2, 3);
        int[] priority = {0, 1, 2, 3};
        Comparator<Integer> order = Comparator.comparingInt(index -> priority[index]);
        List<Integer> first = EntityGoalTargetSelection.first(input, order, 2);
        priority[0] = 10;
        priority[3] = -1;
        assertEquals(List.of(3, 1), EntityGoalTargetSelection.first(input, order, 2));
        assertEquals(List.of(0, 1), first);
    }
}
