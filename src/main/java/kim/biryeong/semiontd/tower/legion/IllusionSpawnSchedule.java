package kim.biryeong.semiontd.tower.legion;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Predicate;

final class IllusionSpawnSchedule<T> {
    private final Comparator<Pending<T>> order = Comparator
            .<Pending<T>>comparingLong(pending -> pending.dueTick)
            .thenComparingLong(pending -> pending.sequence);
    private final PriorityQueue<Pending<T>> pending = new PriorityQueue<>(order);
    private long currentTick;
    private long sequence;

    void enqueue(T value, int delayTicks) {
        pending.add(new Pending<>(value, currentTick + delayTicks, sequence++));
    }

    void tick(int maximum, Predicate<T> spawn) {
        if (pending.isEmpty()) {
            return;
        }
        drain(null, maximum, spawn);
        currentTick++;
    }

    void tick(Predicate<T> scope, int maximum, Predicate<T> spawn) {
        drain(scope, maximum, spawn);
        if (pending.stream().allMatch(entry -> scope.test(entry.value))) {
            pending.forEach(entry -> entry.dueTick--);
            return;
        }
        PriorityQueue<Pending<T>> advanced = new PriorityQueue<>(order);
        while (!pending.isEmpty()) {
            Pending<T> entry = pending.remove();
            if (scope.test(entry.value)) {
                entry.dueTick--;
            }
            advanced.add(entry);
        }
        pending.addAll(advanced);
    }

    void removeIf(Predicate<T> predicate) {
        pending.removeIf(entry -> predicate.test(entry.value));
    }

    void clear() {
        pending.clear();
        currentTick = 0;
        sequence = 0;
    }

    private void drain(Predicate<T> scope, int maximum, Predicate<T> spawn) {
        int spawned = 0;
        while (spawned < maximum) {
            Pending<T> next = pending.peek();
            if (scope != null && next != null && !scope.test(next.value)) {
                next = pending.stream()
                        .filter(entry -> entry.dueTick <= currentTick && scope.test(entry.value))
                        .min(order).orElse(null);
            }
            if (next == null || next.dueTick > currentTick) {
                return;
            }
            if (next == pending.peek()) {
                pending.remove();
            } else {
                pending.remove(next);
            }
            if (spawn.test(next.value)) {
                spawned++;
            }
        }
    }

    private static final class Pending<T> {
        private final T value;
        private long dueTick;
        private final long sequence;

        private Pending(T value, long dueTick, long sequence) {
            this.value = value;
            this.dueTick = dueTick;
            this.sequence = sequence;
        }
    }
}
