package kim.biryeong.semiontd.game.replay.benchmark;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

public final class CombatBenchmarkHooks {
    public static final String ENABLED_PROPERTY = "semiontd.benchmark.enabled";
    private static MinecraftServer observedServer;
    private static Listener listener;
    private static long baseWaitCalls;

    private CombatBenchmarkHooks() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    public static void install(MinecraftServer server, Listener observer) {
        requireOwner(server);
        if (!enabled() || !(server instanceof GameTestServer)) {
            throw new IllegalStateException("Benchmark hooks require an opted-in GameTest server.");
        }
        if (listener != null) {
            throw new IllegalStateException("A benchmark observer is already installed.");
        }
        observedServer = server;
        listener = Objects.requireNonNull(observer);
    }

    public static void remove(MinecraftServer server) {
        requireOwner(server);
        if (observedServer == server) {
            listener = null;
            observedServer = null;
        }
    }

    public static long baseWaitCallCount(MinecraftServer server) {
        requireOwner(server);
        return baseWaitCalls;
    }

    public static void beforeFrame(MinecraftServer server) {
        if (enabled() && observedServer == server && listener != null) {
            listener.beforeFrame(server);
        }
    }

    public static void afterFrame(MinecraftServer server) {
        if (enabled() && observedServer == server && listener != null) {
            listener.afterFrame(server);
        }
    }

    public static void beforeNativeTick(MinecraftServer server) {
        if (enabled() && observedServer == server && listener != null) {
            listener.beforeNativeTick(server);
        }
    }

    public static void afterNativeTick(MinecraftServer server) {
        if (enabled() && observedServer == server && listener != null) {
            listener.afterNativeTick(server);
        }
    }

    public static void waitUntilNextTick(GameTestServer server) {
        if (!enabled()) {
            throw new IllegalStateException("Native benchmark pacing requires explicit opt-in.");
        }
        requireOwner(server);
        if (observedServer == server && listener != null) {
            listener.beforeWait(server);
        }
        try {
            NativeWait.HANDLE.invokeExact(server);
            baseWaitCalls++;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot invoke the native server pacing loop.", failure);
        } finally {
            if (observedServer == server && listener != null) {
                listener.afterWait(server);
            }
        }
    }

    private static void requireOwner(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Benchmark hooks require the server owner thread.");
        }
    }

    private static final class NativeWait {
        private static final MethodHandle HANDLE = createHandle();

        private static MethodHandle createHandle() {
            try {
                return MethodHandles.privateLookupIn(GameTestServer.class, MethodHandles.lookup())
                        .findSpecial(MinecraftServer.class, "waitUntilNextTick", MethodType.methodType(void.class),
                                GameTestServer.class);
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    public interface Listener {
        default void beforeFrame(MinecraftServer server) { }
        default void afterFrame(MinecraftServer server) { }
        default void beforeNativeTick(MinecraftServer server) { }
        default void afterNativeTick(MinecraftServer server) { }
        default void beforeWait(MinecraftServer server) { }
        default void afterWait(MinecraftServer server) { }
    }
}
