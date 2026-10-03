package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.developer.DeveloperTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The 개발자 builder.
 *
 * <p>Base stats sit near the medians for their tier. The edge comes entirely from the preparation
 * phase: reviewed patches that land next round, hotfixes that land now and leave a defect, and the
 * defects themselves, which are as often useful as harmful.
 */
public final class DeveloperTowerJob extends SemionJob {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "developer");

    public DeveloperTowerJob() {
        super(
                ID,
                Component.literal("개발자 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>작업대를 세워 패치 슬롯을 확보하고 알파를 여러 기 놓으세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>알파에서 캐낸 좋은 버그를 재현으로 정식판에 옮기세요.</gray>"),
                        SemionText.mini("<red><bold>주의</bold></red> <gray>핫픽스는 즉시 적용되지만 불안정이 쌓여 웨이브 중 타워가 멈춥니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return DeveloperTowers.isDeveloperTower(towerType);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return DeveloperTowers.isDeveloperTower(towerType);
    }
}
