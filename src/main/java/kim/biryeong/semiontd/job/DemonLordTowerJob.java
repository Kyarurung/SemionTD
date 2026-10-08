package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.tower.demonlord.DemonLordIncome;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The demon lord builder: the player is the defense. Skills are bought straight into key slots from
 * the [스킬 배정] window instead of being built as towers, and income units are sent automatically.
 *
 * <p>Round start puts the player into 전투 상태 with a full health pool; killing monsters feeds the
 * level curve, which is this builder's only source of scaling.
 */
public final class DemonLordTowerJob extends SemionJob {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "demon_lord_towers");

    public DemonLordTowerJob() {
        super(
                ID,
                Component.literal("마왕 빌더"),
                List.of(SemionText.mini("<gray>타워 대신 마왕 본인이 레인에서 직접 싸우는 빌더입니다.</gray>"))
        );
    }

    @Override
    public boolean isHyperCarry() {
        return true;
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>타워 관리 창의 <aqua>[스킬 배정]</aqua>에서 <aqua>1~4, 마검 우클릭, F, Q</aqua> 슬롯에 스킬을 다이아로 삽니다. 타워 수를 차지하지 않고, 빼면 전액 환불됩니다.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>마왕이 직접 레인에서 싸웁니다. 인컴 유닛은 에메랄드가 한도의 설정 비율(기본 70%) 이상이면 살 수 있는 가장 비싼 것을 자동으로 보냅니다.</gray>"),
                SemionText.mini("<yellow><bold>성장</bold></yellow> <gray>처치로 레벨과 스탯 포인트를 얻고 <aqua>[스탯 배정]</aqua>에서 분배합니다. 체력이 0이 되면 그 라운드 전투에서 빠집니다.</gray>")
        );
    }

    /**
     * 스킬 제단은 더 이상 레인에 짓지 않습니다. [스킬 배정] 창에서 키 슬롯에 삽니다.
     *
     * <p>그래서 설치 가능한 타워는 없고, 증강이 주는 추가 타워만 공용 경로로 열립니다.
     */
    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return false;
    }

    /** 제단 타입은 스킬 수치를 담는 그릇이라 카탈로그 내보내기에서는 여전히 이 빌더 소속입니다. */
    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return DemonLordTowers.isDemonLordTower(towerType);
    }

    /**
     * 인컴 유닛은 자동 전송만 보냅니다. 소환이 공짜인 샌드박스에서는 연습을 위해 직접 보낼 수 있습니다.
     */
    @Override
    public boolean canUseSummon(JobContext context, SummonMonsterType summonType) {
        return DemonLordIncome.isSending(context.player().uuid())
                || context.game() != null && context.game().summonsAreFree();
    }
}
