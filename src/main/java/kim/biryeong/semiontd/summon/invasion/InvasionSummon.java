package kim.biryeong.semiontd.summon.invasion;

import java.util.List;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.BasicIncomeSummon;
import kim.biryeong.semiontd.summon.SummonContext;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 마왕 인컴 타워가 보내는 침공군 유닛. 유닛마다 이동·공격 틀({@link InvasionUnits})과 공격 방식
 * ({@link InvasionAttacks}), 따로 도는 능력({@link InvasionGoals})을 붙입니다.
 *
 * <p>능력 수치는 summons.json의 {@code abilityValues}로 조정할 수 있고, 없으면 여기 기본값을 씁니다.
 */
public final class InvasionSummon extends BasicIncomeSummon {
    private final InvasionUnits.Profile profile;

    public InvasionSummon(SummonConfig.SummonDefinition definition) {
        super(definition);
        this.profile = InvasionUnits.profile(definition.id());
    }

    @Override
    public Monster createMonster(SummonContext context, TeamId targetTeam, int targetLaneId, int scalingRound) {
        Monster monster = super.createMonster(context, targetTeam, targetLaneId, scalingRound);
        monster.applyCombatProfile(profile.moveSpeed(), profile.attackRange(), profile.intervalTicks());
        return monster;
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        int hit = profile.hitDelayTicks();
        switch (id()) {
            case "goblin_scout" -> entity.setAttackStyle(InvasionAttacks.goblin(hit));
            case "elf_assassin" -> {
                entity.setStealthCapable(true);
                entity.setAttackStyle(InvasionAttacks.single(hit, InvasionAttacks.facing(InvasionVfx::elfSlash)));
            }
            case "dark_priest" -> {
                double splash = abilityValue("splashRadius", 2.5);
                entity.setAttackStyle(InvasionAttacks.area(hit, splash,
                        InvasionAttacks.at(seed -> InvasionVfx.priestBlast(splash, seed))));
                return List.of(new InvasionGoals.AreaHeal(entity,
                        abilityValue("healRadius", 6.0),
                        abilityValue("healAmount", 16.0),
                        (int) abilityValue("healCooldownTicks", 60.0),
                        (int) abilityValue("healRetryTicks", 10.0)));
            }
            case "dwarf_gunner" -> entity.setAttackStyle(InvasionAttacks.pierce(hit,
                    profile.attackRange() + abilityValue("pierceOvershoot", 3.0),
                    abilityValue("pierceWidth", 0.6)));
            case "troll_javelineer" -> {
                entity.setAttackStyle(InvasionAttacks.javelin(hit, abilityValue("javelinBlocksPerTick", 1.2)));
                return List.of(new InvasionGoals.Regeneration(entity, abilityValue("regenPerSecond", 0.02)));
            }
            case "orc_warrior" -> {
                entity.setAttackStyle(InvasionAttacks.single(hit, null));
                return List.of(new InvasionGoals.Berserk(entity,
                        abilityValue("berserkHealthRatio", 0.5),
                        abilityValue("berserkDamageBonus", 0.35),
                        abilityValue("berserkAttackSpeedBonus", 0.35)));
            }
            case "necromancer" -> {
                entity.setAttackStyle(InvasionAttacks.single(hit, InvasionAttacks.at(InvasionVfx::necroBolt)));
                return List.of(new InvasionGoals.RaiseSkeletons(entity,
                        (int) abilityValue("summonCount", 6.0),
                        (int) abilityValue("summonIntervalTicks", 5.0),
                        (int) abilityValue("summonCooldownTicks", 320.0),
                        hit,
                        (int) abilityValue("maxMinions", 6.0),
                        abilityValue("minionHealthRatio", 0.12),
                        abilityValue("minionDamageRatio", 0.15)));
            }
            case "siege_golem" -> {
                entity.setIgnoresDefenses(true);
                entity.setAttackStyle(InvasionAttacks.single(hit,
                        InvasionAttacks.at(seed -> InvasionVfx.groundSlam(1.6, seed))));
            }
            case "legion_commander" -> {
                entity.setAttackStyle(InvasionAttacks.single(hit, InvasionAttacks.facing(InvasionVfx::commanderSlash)));
                return List.of(new InvasionGoals.CommandAura(entity,
                        abilityValue("auraRadius", 7.0),
                        abilityValue("auraDamageReduction", 0.2),
                        abilityValue("auraAttackBonus", 0.2)));
            }
            case "ogre_champion" -> {
                double splash = abilityValue("splashRadius", 2.8);
                entity.setAttackStyle(InvasionAttacks.area(hit, splash,
                        InvasionAttacks.at(seed -> InvasionVfx.groundSlam(splash, seed))));
            }
            default -> {
            }
        }
        return List.of();
    }
}
