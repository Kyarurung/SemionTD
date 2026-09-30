package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ClientTickScaleTest {
    @Test
    void doubledServerTicksHalveClientTimes() {
        float ratio = ClientTickScale.ratio(40.0F);
        assertEquals(2.0F, ratio, 1.0e-6F);
        assertEquals(20, ClientTickScale.toClientTicks(ratio, 40), "A 2 s cooldown still shows as 2 s on the client.");
        assertEquals(1, ClientTickScale.toClientTicks(ratio, 1), "A positive duration never rounds down to nothing.");
        assertEquals(0, ClientTickScale.toClientTicks(ratio, 0));
        assertEquals(64, ClientTickScale.toServerTicks(ratio, 32), "A 32-tick client animation spans 64 server ticks.");
    }

    @Test
    void normalAndSlowedServersNeedNoConversion() {
        assertEquals(1.0F, ClientTickScale.ratio(20.0F), 1.0e-6F);
        assertEquals(1.0F, ClientTickScale.ratio(10.0F), 1.0e-6F, "The client slows down with the server, so no scaling.");
        assertEquals(1.0F, ClientTickScale.ratio(Float.NaN), 1.0e-6F);
        assertEquals(32, ClientTickScale.toServerTicks(1.0F, 32));
    }
}
