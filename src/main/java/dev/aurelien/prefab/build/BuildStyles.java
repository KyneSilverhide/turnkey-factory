package dev.aurelien.prefab.build;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Styles texturés. Chaque module est une palette de blocs <b>visuellement proches</b> (famille pierre)
 * pour un rendu varié mais cohérent — pas de laine ni de blocs exotiques. Résolution des blocs moddés
 * par identifiant avec fallback vanilla : aucune dépendance dure.
 */
public final class BuildStyles {
    private BuildStyles() {}

    /** Style à appliquer pour un thème donné. */
    public static BuildStyle of(Theme theme) {
        return switch (theme) {
            case BRICK -> brickStyle();
            default -> vanillaStyle();
        };
    }

    /** Style « pierre » : briques de pierre dominantes, mouchetées de cobblestone/andésite/fissures. */
    private static BuildStyle vanillaStyle() {
        Palette wall = Palette.builder()
                .add(state(Blocks.STONE_BRICKS), 10)
                .add(state(Blocks.CRACKED_STONE_BRICKS), 3)
                .add(state(Blocks.COBBLESTONE), 2)
                .add(state(Blocks.ANDESITE), 2)
                .add(state(Blocks.STONE), 2)
                .add(state(Blocks.MOSSY_STONE_BRICKS), 1)
                .build();

        Palette foundation = Palette.builder()
                .add(state(Blocks.COBBLESTONE), 6)
                .add(state(Blocks.STONE_BRICKS), 3)
                .add(state(Blocks.ANDESITE), 2)
                .add(state(Blocks.MOSSY_COBBLESTONE), 1)
                .build();

        Palette floor = Palette.builder()
                .add(state(Blocks.SMOOTH_STONE), 8)
                .add(state(Blocks.POLISHED_ANDESITE), 2)
                .add(state(Blocks.STONE_BRICKS), 1)
                .build();

        Palette roof = Palette.builder()
                .add(state(Blocks.STONE_BRICKS), 6)
                .add(state(Blocks.ANDESITE), 2)
                .add(state(Blocks.COBBLESTONE), 2)
                .build();

        Palette pillar = Palette.builder()
                .add(state(Blocks.ANDESITE), 5)
                .add(state(Blocks.STONE_BRICKS), 3)
                .add(state(Blocks.CRACKED_STONE_BRICKS), 1)
                .build();

        Palette cornice = Palette.of(state(Blocks.SMOOTH_STONE));
        Palette window = Palette.of(state(Blocks.GLASS_PANE));
        Palette lamp = Palette.of(state(Blocks.SEA_LANTERN));
        // Toit pentu : tuiles en deepslate (ardoise foncée) — fort contraste avec les stone bricks clairs.
        Palette roofStair = Palette.of(state(Blocks.DEEPSLATE_TILE_STAIRS));
        Palette roofRidge = Palette.of(state(Blocks.DEEPSLATE_TILES));
        Palette roofBeam = Palette.of(state(Blocks.STRIPPED_SPRUCE_LOG)); // poutres apparentes (look entrepôt)
        Palette trimStair = Palette.of(state(Blocks.STONE_BRICK_STAIRS));
        Palette trimSlab = Palette.of(state(Blocks.STONE_BRICK_SLAB));
        Palette parapetWall = Palette.of(state(Blocks.STONE_BRICK_WALL));

        return new BuildStyle(floor, foundation, wall, pillar, cornice, roof, roofStair, roofRidge, roofBeam,
                trimStair, trimSlab, parapetWall, window, lamp);
    }

    /**
     * Style « brique » : brique rouge en accent, granite/andésite dominants. La brique (item
     * {@link Blocks#BRICKS}) coûte cher à produire en survie (argile + cuisson au four), contrairement
     * au granite/andésite (minage direct) — la palette reste donc volontairement à dominante pierre,
     * la brique n'apparaissant qu'en bandes/accents pour garder l'identité visuelle « usine en brique ».
     */
    private static BuildStyle brickStyle() {
        // Mur : granite dominant, brique rouge en accent (~40 %). L'andésite reste réservée au
        // soubassement (palette foundation, 1re rangée) : pas de mouchetage ici. Si Create Deco est
        // présent, on mouchette de variantes de brique rouge (mêmes tons, appareils différents → casse
        // la monotonie). Absent : tout retombe sur la brique vanilla (rendu inchangé, juste moins fréquent).
        Palette wall = Palette.builder()
                .add(state(Blocks.BRICKS), 5)
                .add(resolve("createdeco:long_red_bricks", Blocks.BRICKS), 1)
                .add(resolve("createdeco:short_red_bricks", Blocks.BRICKS), 1)
                .add(resolve("createdeco:tiled_red_bricks", Blocks.BRICKS), 1)
                .add(resolve("createdeco:cracked_red_bricks", Blocks.BRICKS), 1)
                .add(resolve("createdeco:dean_bricks", Blocks.BRICKS), 1)   // patch d'accent chaud
                .add(state(Blocks.GRANITE), 6)
                .add(state(Blocks.POLISHED_GRANITE), 3)
                .build();

        // Premier niveau (soubassement) : variantes d'andésite uniquement (pas de smooth stone).
        Palette foundation = Palette.builder()
                .add(state(Blocks.POLISHED_ANDESITE), 6)
                .add(state(Blocks.ANDESITE), 3)
                .build();

        // Sol intérieur : granite dominant, brique en accent léger (pas de smooth stone).
        Palette floor = Palette.builder()
                .add(state(Blocks.GRANITE), 4)
                .add(state(Blocks.POLISHED_GRANITE), 3)
                .add(state(Blocks.BRICKS), 2)
                .build();

        Palette roof = Palette.builder()
                .add(state(Blocks.BRICKS), 3)
                .add(resolve("createdeco:tiled_red_bricks", Blocks.BRICKS), 1)
                .add(resolve("createdeco:long_red_bricks", Blocks.BRICKS), 1)
                .add(state(Blocks.GRANITE), 4)
                .add(state(Blocks.POLISHED_GRANITE), 3)
                .build();

        Palette pillar = Palette.builder()      // piliers granite/andésite, brique en accent
                .add(state(Blocks.BRICKS), 3)
                .add(resolve("createdeco:short_red_bricks", Blocks.BRICKS), 1)
                .add(resolve("createdeco:cracked_red_bricks", Blocks.BRICKS), 1)
                .add(state(Blocks.GRANITE), 3)
                .add(state(Blocks.POLISHED_GRANITE), 3)
                .build();

        Palette cornice = Palette.of(state(Blocks.POLISHED_GRANITE));
        Palette window = Palette.of(state(Blocks.GLASS_PANE));
        Palette lamp = Palette.of(state(Blocks.SEA_LANTERN));
        // Toit ardoise foncée : fort contraste avec la brique rouge (toiture d'usine classique).
        Palette roofStair = Palette.of(state(Blocks.DEEPSLATE_TILE_STAIRS));
        Palette roofRidge = Palette.of(state(Blocks.DEEPSLATE_TILES));
        Palette roofBeam = Palette.of(state(Blocks.STRIPPED_DARK_OAK_LOG));
        // Coiffes de piliers en variantes d'andésite (accent gris sur la brique).
        Palette trimStair = Palette.builder()
                .add(state(Blocks.POLISHED_ANDESITE_STAIRS), 2)
                .add(state(Blocks.ANDESITE_STAIRS), 1)
                .build();
        Palette trimSlab = Palette.builder()
                .add(state(Blocks.POLISHED_ANDESITE_SLAB), 2)
                .add(state(Blocks.ANDESITE_SLAB), 1)
                .build();
        Palette parapetWall = Palette.of(state(Blocks.ANDESITE_WALL));

        return new BuildStyle(floor, foundation, wall, pillar, cornice, roof, roofStair, roofRidge, roofBeam,
                trimStair, trimSlab, parapetWall, window, lamp);
    }

    private static BlockState state(Block block) {
        return block.defaultBlockState();
    }

    private static BlockState resolve(String id, Block fallback) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
            Block block = BuiltInRegistries.BLOCK.get(rl);
            if (block != Blocks.AIR) {
                return block.defaultBlockState();
            }
        }
        return fallback.defaultBlockState();
    }
}
