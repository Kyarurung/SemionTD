package kim.biryeong.semiontd.tower.demonlord;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.summon.SummonMonsterType;

/**
 * 마왕 전용 인컴 전송.
 *
 * <p>마왕은 인컴 유닛을 직접 고르지 않습니다. 에메랄드가 그 라운드 한도의 설정 비율(기본 70%) 이상
 * 차 있으면, 지금 에메랄드로 살 수 있는 가장 비싼 인컴 유닛을 알아서 보냅니다. 레인에서 직접
 * 싸우는 동안 상점을 열 틈이 없는 빌더라, 쌓이기만 하고 쓰이지 않는 에메랄드를 줄이려는 것입니다.
 *
 * <p>비율은 [인컴 설정] 창의 막대로 바꿉니다. 샌드박스처럼 소환이 공짜인 곳에서는 돌지 않습니다 -
 * 공짜면 매 초 최고가 유닛을 끝없이 보내게 됩니다.
 */
public final class DemonLordIncome {
    /** 지금 자동 전송 중인 플레이어. 이 경로만 마왕의 소환 금지를 통과합니다. */
    private static final Set<UUID> SENDING = ConcurrentHashMap.newKeySet();

    private DemonLordIncome() {
    }

    public static boolean isSending(UUID playerId) {
        return playerId != null && SENDING.contains(playerId);
    }

    /** 1초마다 한 번. 조건을 만족하는 마왕마다 유닛 하나를 보냅니다. */
    public static void tick(SemionGame game) {
        if (game == null || game.summonsAreFree()
                || (game.phase() != RoundPhase.PREPARE_AND_SUMMON && game.phase() != RoundPhase.LANE_WAVE)) {
            return;
        }
        long cap = game.economyConfig().emeraldCapForRound(game.currentRound());
        for (SemionPlayer player : game.players().values()) {
            if (!isDemonLord(player)) {
                continue;
            }
            DemonLordState state = DemonLordStates.get(player.uuid());
            if (state == null || !state.autoIncomeEnabled()) {
                continue;
            }
            long emerald = player.economy().emerald();
            if (!shouldSend(emerald, cap, state.autoIncomeThreshold())) {
                continue;
            }
            Optional<SummonMonsterType> pick = mostExpensiveAffordable(
                    game.summonShop().all(), emerald, SummonMonsterType::gasCost, SummonMonsterType::incomeGain);
            if (pick.isEmpty()) {
                continue;
            }
            SENDING.add(player.uuid());
            try {
                game.summonMonster(player.uuid(), pick.get().id());
            } finally {
                SENDING.remove(player.uuid());
            }
        }
    }

    /** 에메랄드가 한도의 {@code threshold} 비율 이상인지. 한도가 없으면 보내지 않습니다. */
    public static boolean shouldSend(long emerald, long cap, double threshold) {
        if (cap <= 0L) {
            return false;
        }
        double ratio = Math.max(0.0, Math.min(1.0, threshold));
        return emerald >= Math.ceil(cap * ratio);
    }

    /**
     * 예산 안에서 가장 비싼 인컴 유닛. 값이 같으면 인컴을 더 주는 쪽입니다.
     *
     * <p>인컴이 0인 유닛(유틸 소환 등)은 인컴 전송이 아니므로 고르지 않습니다.
     */
    public static <T> Optional<T> mostExpensiveAffordable(
            Collection<T> candidates,
            long budget,
            ToLongFunction<T> cost,
            ToLongFunction<T> income
    ) {
        return candidates.stream()
                .filter(candidate -> income.applyAsLong(candidate) > 0L)
                .filter(candidate -> cost.applyAsLong(candidate) > 0L && cost.applyAsLong(candidate) <= budget)
                .max(Comparator.comparingLong(cost).thenComparingLong(income));
    }

    public static boolean isDemonLord(SemionPlayer player) {
        return player != null && player.job().map(job -> DemonLordTowerJob.ID.equals(job.id())).orElse(false);
    }
}
