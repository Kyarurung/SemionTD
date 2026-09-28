package kim.biryeong.semiontd.tower.demonlord;

/**
 * 패시브 자리. 스킬 키 일곱 개(1~4, 마검 우클릭, F, Q) 뒤의 8·9번입니다.
 *
 * <p>키에 묶이지 않으므로 핫바에는 나오지 않고 [스킬 배정] 창에서만 보입니다.
 */
public enum DemonLordPassiveSlot {
    EIGHT("8"),
    NINE("9");

    private final String label;

    DemonLordPassiveSlot(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
