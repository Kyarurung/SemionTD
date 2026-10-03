package com.faboslav.friendsandfoes.common.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import java.util.function.UnaryOperator;

/** Local replacement for the removed helper; retains named block IDs and block-state Name migration. */
public final class LegacyBlockRenameFix extends DataFix {
    private final String name;
    private final UnaryOperator<String> renamer;

    private LegacyBlockRenameFix(Schema schema, String name, UnaryOperator<String> renamer) {
        super(schema, false);
        this.name = name;
        this.renamer = renamer;
    }

    public static DataFix create(Schema schema, String name, UnaryOperator<String> renamer) {
        return new LegacyBlockRenameFix(schema, name, renamer);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<Pair<String, String>> namedBlock = DSL.named(References.BLOCK_NAME.typeName(), NamespacedSchema.namespacedString());
        if (!getInputSchema().getType(References.BLOCK_NAME).equals(namedBlock)) {
            throw new IllegalStateException("Unexpected named block schema for lightning-rod migration");
        }
        return TypeRewriteRule.seq(
                fixTypeEverywhere(name, namedBlock, ops -> pair -> pair.mapSecond(renamer)),
                fixTypeEverywhereTyped(name + " state", getInputSchema().getType(References.BLOCK_STATE),
                        typed -> typed.update(DSL.remainderFinder(), dynamic -> dynamic.update("Name",
                                this::renameName))));
    }

    private <T> com.mojang.serialization.Dynamic<T> renameName(com.mojang.serialization.Dynamic<T> value) {
        var name = value.asString().result();
        return name.isPresent() ? value.createString(renamer.apply(name.get())) : value;
    }
}