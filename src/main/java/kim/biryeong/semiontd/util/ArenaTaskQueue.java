package kim.biryeong.semiontd.util;

import java.util.concurrent.ConcurrentLinkedQueue;

final class ArenaTaskQueue {
    private final ConcurrentLinkedQueue<Task> tasks = new ConcurrentLinkedQueue<>();

    void submit(long gameTime, Runnable action, int delay) {
        tasks.add(new Task(gameTime - 1 + Math.max(0, delay), action));
    }

    void runTasks(long gameTime) {
        tasks.removeIf(task -> {
            if (gameTime < task.deadline()) {
                return false;
            }
            task.action().run();
            return true;
        });
    }

    boolean isEmpty() {
        return tasks.isEmpty();
    }

    private record Task(long deadline, Runnable action) {
    }
}
