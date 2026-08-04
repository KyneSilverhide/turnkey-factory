package dev.aurelien.prefab.build;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Détecte le terrain « naturel » (généré par le monde), par opposition à tout ce qu'un joueur a pu
 * poser (construction, coffre…). Partagé entre le bloc de contrôle (protection anti-obstruction) et
 * la niveleuse (filtre « sol uniquement » : elle ne retire/ne remplace jamais un bloc du joueur).
 */
public final class NaturalTerrain {
    private NaturalTerrain() {}

    /** Vrai si {@code s} appartient au terrain/à la roche/aux minerais/à la végétation naturels. */
    public static boolean isNaturalGround(BlockState s) {
        // Terrain & roche
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)) return true;
        // Minerais
        if (s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                || s.is(BlockTags.GOLD_ORES) || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES)
                || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES)) return true;
        // Végétation
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS)
                || s.is(BlockTags.CORAL_BLOCKS) || s.is(BlockTags.WART_BLOCKS) || s.is(BlockTags.NYLIUM)) return true;
        // Glace, neige
        if (s.is(BlockTags.ICE) || s.is(BlockTags.SNOW)) return true;

        return NATURAL_BLOCKS.contains(s.getBlock());
    }

    /**
     * Vrai si {@code s} est un bloc de SURFACE naturel (terre, sable, roche de base, neige, glace…),
     * sans les minerais ni la végétation (troncs, feuilles, fleurs…) que couvre
     * {@link #isNaturalGround}. Sert au texturiseur : il ne remplace que le sol proprement dit, jamais
     * un arbre ou un minerai qui reposerait dessus.
     */
    public static boolean isSurfaceGround(BlockState s) {
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(BlockTags.NYLIUM)) return true;
        return SURFACE_BLOCKS.contains(s.getBlock());
    }

    /** Blocs de surface supplémentaires non couverts par les tags ci-dessus. */
    private static final Set<Block> SURFACE_BLOCKS = Set.of(
            Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD, Blocks.MOSS_BLOCK,
            Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.CALCITE, Blocks.POWDER_SNOW
    );

    /** Blocs naturels supplémentaires non couverts par les tags ci-dessus. */
    private static final Set<Block> NATURAL_BLOCKS = Set.of(
            Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.MANGROVE_ROOTS,
            Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.VINE, Blocks.GLOW_LICHEN,
            Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE, Blocks.CALCITE, Blocks.MAGMA_BLOCK,
            Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.GLOWSTONE, Blocks.OBSIDIAN, Blocks.ANCIENT_DEBRIS,
            Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.BAMBOO, Blocks.SUGAR_CANE, Blocks.CACTUS,
            Blocks.PUMPKIN, Blocks.MELON, Blocks.SWEET_BERRY_BUSH, Blocks.BIG_DRIPLEAF, Blocks.SMALL_DRIPLEAF,
            Blocks.HANGING_ROOTS, Blocks.SPORE_BLOSSOM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK,
            Blocks.MUSHROOM_STEM, Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST, Blocks.SCULK_SENSOR,
            Blocks.SCULK_SHRIEKER, Blocks.POWDER_SNOW, Blocks.GILDED_BLACKSTONE,
            Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE
    );
}
