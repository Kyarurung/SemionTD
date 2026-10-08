package kim.biryeong.semiontd.tower.plant;

import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;

public final class PlantTowerTickScaleFixture {
    private PlantTowerTickScaleFixture() {
    }

    public static int dashTicks(SemionTowerEntity source) {
        return PandaTower.dashTicks(source);
    }
}
