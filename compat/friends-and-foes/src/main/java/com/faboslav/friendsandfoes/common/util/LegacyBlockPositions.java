package com.faboslav.friendsandfoes.common.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Preserves the pre-26.3 bounded-box search and Manhattan-distance iteration order. */
public final class LegacyBlockPositions {
    private LegacyBlockPositions() { }

    public static Iterable<BlockPos> withinManhattan(BlockPos origin, int xRange, int yRange, int zRange) {
        List<BlockPos> result = new ArrayList<>();
        for (int distance = 0; distance <= xRange + yRange + zRange; distance++) {
            for (int x = -Math.min(xRange, distance); x <= Math.min(xRange, distance); x++) {
                int yLimit = Math.min(yRange, distance - Math.abs(x));
                for (int y = -yLimit; y <= yLimit; y++) {
                    int z = distance - Math.abs(x) - Math.abs(y);
                    if (z <= zRange) {
                        result.add(origin.offset(x, y, z));
                        if (z != 0) result.add(origin.offset(x, y, -z));
                    }
                }
            }
        }
        return result;
    }
}