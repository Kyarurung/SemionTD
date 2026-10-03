package lol.sylvie.sylcurity.block;

import java.util.Map;
import net.minecraft.core.Direction;
import com.mojang.datafixers.util.Pair;

public class BlockStateUtil {
	public static final Map<Direction, Pair<Integer, Integer>> DIRECTION_ROTATIONS = Map.of(
			Direction.NORTH, Pair.of(0, 0),
			Direction.EAST, Pair.of(0, 90),
			Direction.SOUTH, Pair.of(0, 180),
			Direction.WEST, Pair.of(0, 270),
			Direction.UP, Pair.of(270, 0),
			Direction.DOWN, Pair.of(90, 0)
	);
}
