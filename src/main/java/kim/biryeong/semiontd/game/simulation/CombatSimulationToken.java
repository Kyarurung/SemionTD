package kim.biryeong.semiontd.game.simulation;

public record CombatSimulationToken(
        long generation,
        long revision,
        long logicalTick,
        int round,
        long inputSequence
) {
}
