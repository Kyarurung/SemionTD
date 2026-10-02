package kim.biryeong.semiontd.game;

import net.minecraft.server.MinecraftServer;

/**
 * 전투 배속(서버 틱 속도 상승) 때 클라이언트 쪽 시간과 맞추는 환산.
 *
 * <p>서버가 초당 40틱으로 돌아도 클라이언트는 초당 20틱보다 빨라지지 않습니다(느려지는 쪽만 따라갑니다). 그래서
 * 클라이언트가 틱으로 세는 것들 - 디스플레이 보간 시간, 아이템 쿨타임 표시, 바닐라 몹의 동작(판다 구르기 등),
 * 플레이어의 이동 - 은 서버 틱 그대로 보내면 배속만큼 느리게 보입니다. 여기서 두 시간을 오갑니다.
 */
public final class ClientTickScale {
    private static final float CLIENT_TICK_RATE = 20.0F;

    private ClientTickScale() {
    }

    /** 서버 틱이 클라이언트 틱보다 몇 배 빠른지. 배속이 아니면 1입니다. */
    public static float ratio(MinecraftServer server) {
        return server == null ? 1.0F : ratio(server.tickRateManager().tickrate());
    }

    /** 서버 틱 속도(초당 틱)로 본 배속. 클라이언트는 20보다 빨라지지 않고 느려지는 쪽은 따라가므로 1 밑으로는 내려가지 않습니다. */
    public static float ratio(float serverTickRate) {
        return Float.isFinite(serverTickRate) ? Math.max(1.0F, serverTickRate / CLIENT_TICK_RATE) : 1.0F;
    }

    /** 서버 틱 수를 같은 실제 시간의 클라이언트 틱 수로 바꿉니다. 0은 0으로 두고, 양수는 최소 1입니다. */
    public static int toClientTicks(MinecraftServer server, int serverTicks) {
        return toClientTicks(ratio(server), serverTicks);
    }

    public static int toClientTicks(float ratio, int serverTicks) {
        if (serverTicks <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(serverTicks / Math.max(1.0F, ratio)));
    }

    /** 클라이언트 틱 수를 같은 실제 시간의 서버 틱 수로 바꿉니다. */
    public static int toServerTicks(MinecraftServer server, int clientTicks) {
        return toServerTicks(ratio(server), clientTicks);
    }

    public static int toServerTicks(float ratio, int clientTicks) {
        return Math.max(0, Math.round(clientTicks * Math.max(1.0F, ratio)));
    }
}
