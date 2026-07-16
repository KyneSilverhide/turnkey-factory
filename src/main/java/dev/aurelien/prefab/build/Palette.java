package dev.aurelien.prefab.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Palette de blocs pondérée pour le texturing. Chaque cellule pioche un bloc de façon
 * <b>déterministe</b> à partir de sa position (hash) : le rendu est stable d'un recalcul à l'autre
 * (indispensable pour la détection d'obstruction idempotente) et ne scintille pas.
 */
public final class Palette {
    private final BlockState[] states;
    private final int[] cumulative; // poids cumulés
    private final int total;

    private Palette(BlockState[] states, int[] cumulative, int total) {
        this.states = states;
        this.cumulative = cumulative;
        this.total = total;
    }

    public static Palette of(BlockState single) {
        return new Palette(new BlockState[]{single}, new int[]{1}, 1);
    }

    public static Builder builder() {
        return new Builder();
    }

    public BlockState pick(BlockPos pos) {
        if (states.length == 1) {
            return states[0];
        }
        int r = Math.floorMod(mix(pos.getX(), pos.getY(), pos.getZ()), total);
        for (int i = 0; i < cumulative.length; i++) {
            if (r < cumulative[i]) {
                return states[i];
            }
        }
        return states[states.length - 1];
    }

    /** Hash déterministe d'une position (mélange type SplitMix). */
    private static int mix(int x, int y, int z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (int) (h & 0x7FFFFFFF);
    }

    public static final class Builder {
        private final List<BlockState> states = new ArrayList<>();
        private final List<Integer> weights = new ArrayList<>();

        public Builder add(BlockState state, int weight) {
            if (weight > 0) {
                states.add(state);
                weights.add(weight);
            }
            return this;
        }

        public Palette build() {
            int[] cumulative = new int[states.size()];
            int total = 0;
            for (int i = 0; i < states.size(); i++) {
                total += weights.get(i);
                cumulative[i] = total;
            }
            return new Palette(states.toArray(new BlockState[0]), cumulative, total);
        }
    }
}
