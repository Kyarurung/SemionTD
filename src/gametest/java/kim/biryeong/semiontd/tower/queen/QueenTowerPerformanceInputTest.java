package kim.biryeong.semiontd.tower.queen;

public final class QueenTowerPerformanceInputTest {
    private QueenTowerPerformanceInputTest() {
    }

    public static QueenCard preset(QueenCardTower tower, int index) {
        QueenCard card = new QueenCard(QueenCard.Suit.values()[index % QueenCard.Suit.values().length], index % 13 + 1);
        tower.assignCard(card);
        return card;
    }
}
