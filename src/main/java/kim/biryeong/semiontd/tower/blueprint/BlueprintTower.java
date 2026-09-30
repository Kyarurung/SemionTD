package kim.biryeong.semiontd.tower.blueprint;

import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;

/** 설계도로 세운 타워. 능력치는 설계도가 만든 타입에서 오고, 모듈은 다음 단계에서 이 클래스의 훅에 붙습니다. */
public class BlueprintTower extends ProductionTower {
    public BlueprintTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }
}
