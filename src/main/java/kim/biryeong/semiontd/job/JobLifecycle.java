package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.summon.SummonMonsterType;

interface JobLifecycle {
    JobLifecycle NONE = new JobLifecycle() {};

    default void onSelected(JobContext context) {
    }

    default void onMatchStarted(JobContext context) {
    }

    default void onRoundStarted(JobContext context, int round) {
    }

    default void onRoundEnded(JobContext context, int round) {
    }

    default void onEliminated(JobContext context) {
    }

    default void onMatchClosed(JobContext context) {
    }

    default void onSummonedMonster(JobContext context, SummonMonsterType summonType, Monster monster) {
    }

    default void onMonsterKilled(JobContext context, Monster monster, long mineralReward) {
    }
}
