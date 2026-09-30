package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStates;
import kim.biryeong.semiontd.tower.blueprint.BlueprintTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 빌더 빌더: 정해진 타워가 없고, 플레이어가 능력치·겉모습을 골라 설계한 타워만 세웁니다.
 *
 * <p>설계도는 한 경기 동안만 살고 만든 사람만 세울 수 있습니다. 세게 설계할수록 설치 가격이 가파르게 오릅니다.
 */
public final class BlueprintTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "blueprint");

    public BlueprintTowerJob() {
        super(
                ID,
                Component.literal("빌더 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>정해진 타워가 없습니다. 능력치와 겉모습을 골라 직접 타워를 설계하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>설계도는 바꿀 수 없습니다. 더 센 타워는 새로 설계해 바꿔 세우세요.</gray>"),
                        SemionText.mini("<red><bold>주의</bold></red> <gray>세게 설계할수록 값이 가파르게 오르고 타워 수도 더 차지합니다.</gray>")
                )
        );
    }

    /** 설계도 타워 중 이 플레이어가 만든 것만 세울 수 있습니다. */
    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (context == null || !BlueprintTowers.isBlueprintTower(towerType)) {
            return false;
        }
        return BlueprintStates.find(towerType)
                .map(blueprint -> blueprint.owner().equals(context.player().uuid()))
                .orElse(false);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return BlueprintTowers.isBlueprintTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        BlueprintStates.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        BlueprintStates.clear(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        BlueprintStates.clear(context.player().uuid());
    }
}
