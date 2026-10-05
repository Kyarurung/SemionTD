package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class MagicSchoolTowerJob extends SemionJob {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "magic_school");

    public MagicSchoolTowerJob() {
        super(ID, Component.literal("마법학교 빌더"), List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>호그와트와 신입생을 배치하세요.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>마법사의 주문을 지정하세요. 신입생은 엑스펠리아르무스로 시작합니다.</gray>"),
                SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>호그와트는 플레이어마다 하나만 설치할 수 있으며, 혼자 남으면 방어에 실패합니다.</gray>")
        ));
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!MagicSchoolTowers.isMagicSchoolTower(towerType)) {
            return false;
        }
        if (!MagicSchoolTowers.HOGWARTS.id().equals(towerType.id()) || context == null) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream().noneMatch(tower ->
                        tower.ownerPlayer().equals(context.player().uuid())
                                && MagicSchoolTowers.isHogwarts(tower.type())))
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return MagicSchoolTowers.isMagicSchoolTower(towerType);
    }

}
