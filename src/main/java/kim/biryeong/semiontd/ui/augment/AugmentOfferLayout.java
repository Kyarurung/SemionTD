package kim.biryeong.semiontd.ui.augment;

public final class AugmentOfferLayout {
    public static final int CARD_WIDTH = 54;
    public static final int CARD_HEIGHT = 90;
    public static final int REROLL_SLOT = 49;
    public static final long STEP_NANOS = 50_000_000L;
    public static final long STAGGER_NANOS = 150_000_000L;
    public static final long REVEAL_NANOS = 450_000_000L;

    private AugmentOfferLayout() { }

    public static int cardAt(int slot) {
        return slot >= 0 && slot < 45 ? slot % 9 / 3 : -1;
    }

    public static int stage(int card, long elapsedNanos) {
        if (card < 0 || card >= 3) throw new IllegalArgumentException("Card index");
        long local = elapsedNanos - card * STAGGER_NANOS;
        return local < 0 ? -1 : (int) Math.min(3, local / STEP_NANOS);
    }
}
