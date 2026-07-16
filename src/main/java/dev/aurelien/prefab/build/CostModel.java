package dev.aurelien.prefab.build;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Coût d'un bloc en RESSOURCES DE BASE (et non l'item exact : ce n'est pas du 1-1). On dépose des
 * matières premières — cobblestone, blocs de briques, deepslate, planches/rondins, verre, torches,
 * fer, cuivre… — et le mod en consomme l'équivalent. On déduit la catégorie depuis l'état du bloc →
 * indépendant du thème.
 *
 * <ul>
 *   <li>végétation / déco bonus (loom, comparateur, barrière) → gratuit ;</li>
 *   <li>mécanismes Create (girder, robinetterie/jauge…) → fer/cuivre, vitrage
 *       Create (weathered iron pane, qui remplace le glass pane vanille) → 1 verre normal comme le
 *       glass pane qu'il remplace, cf. {@link #createCost} (les autres blocs Create non listés, ex.
 *       ventilateur encaissé, restent gratuits) ;</li>
 *   <li>émetteur de lumière (lanternes…) → 1 torche ;</li>
 *   <li>vitrage → 1 verre ; chaîne → 1 fer ;</li>
 *   <li>deepslate (tuiles de toit…) → 1 cobbled deepslate ;</li>
 *   <li>rondin → 1 rondin ; autre bois (planches, caisses, escaliers bois) → 1 planche ;</li>
 *   <li>brique rouge → 1 bloc de briques (pas l'item Brique, qui n'est pas plaçable) ; cuivre → 2 lingots ;</li>
 *   <li>tout le reste (familles pierre) → 1 cobblestone.</li>
 * </ul>
 */
public final class CostModel {
    private CostModel() {}

    @SuppressWarnings("deprecation") // getLightEmission() sans coordonnées : l'émission de base suffit
    public static Map<Item, Integer> costOf(BlockState state) {
        if (state.isAir()) return Map.of();
        if (isVegetation(state)) return Map.of();
        if (isBonusDeco(state)) return Map.of();
        Map<Item, Integer> create = createCost(state);
        if (create != null) return create;
        if (state.getLightEmission() > 0) return lightCost(state);
        if (isGlass(state)) return Map.of(Items.GLASS, 1);
        if (state.is(Blocks.CHAIN)) return Map.of(Items.IRON_INGOT, 1);

        String path = path(state);
        if (path.contains("deepslate")) return Map.of(Items.COBBLED_DEEPSLATE, 1);
        if (state.is(BlockTags.LOGS)) return Map.of(Items.OAK_LOG, 1);
        if (isWood(state)) return Map.of(Items.OAK_PLANKS, 1);
        if (path.contains("brick") && !path.contains("stone")) return Map.of(Items.BRICKS, 1); // bloc de briques
        if (path.contains("copper")) return Map.of(Items.COPPER_INGOT, 2);
        return Map.of(Items.COBBLESTONE, 1); // toute autre pierre (stone bricks, andésite, granite…)
    }

    /**
     * Coût des mécanismes/greebles Create eux-mêmes : la robinetterie/jauge/cuve représente du cuivre
     * (un peu généreux, pour anticiper les décorations à venir). Renvoie {@code null} si {@code state}
     * n'est pas un bloc {@code create:*}, pour retomber sur les règles génériques ci-dessus. Les autres
     * blocs Create non listés ici (ventilateur encaissé, vitrage patiné…) restent gratuits, comme avant.
     */
    private static Map<Item, Integer> createCost(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!"create".equals(id.getNamespace())) return null;
        String path = id.getPath();
        if (path.contains("girder")) return Map.of(Items.IRON_INGOT, 1);
        if (path.contains("fluid_tank") || path.contains("fluid_pipe")
                || path.contains("factory_gauge") || path.contains("valve_handle")) {
            return Map.of(Items.COPPER_INGOT, 2);
        }
        // Vitrage : remplace le glass pane vanille (cf. BuildPlanner) quand Create est chargé — même
        // coût que le glass pane qu'elle remplace, du verre normal suffit (pas d'ingrédient Create).
        if (path.contains("window_pane")) return Map.of(Items.GLASS, 1);
        return Map.of();
    }

    /**
     * Coût d'un émetteur de lumière : 1 torche, plus le matériau du corps pour les cage lamps (cuivre
     * pour la variante cuivre ; la variante andésite reste sur la règle « famille pierre » = 1
     * cobblestone, comme tout autre bloc d'andésite/granite/stone bricks du bâtiment — pas l'item
     * Andésite littéral) ; les simples lanternes/sea lanterns restent à 1 torche.
     */
    private static Map<Item, Integer> lightCost(BlockState s) {
        String path = path(s);
        if (path.contains("andesite")) return Map.of(Items.TORCH, 1, Items.COBBLESTONE, 1);
        if (path.contains("copper")) return Map.of(Items.TORCH, 1, Items.COPPER_INGOT, 2);
        return Map.of(Items.TORCH, 1);
    }

    private static boolean isVegetation(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(Blocks.VINE);
    }

    /** Pièces distinctives du faux climatiseur (loom, comparateur, barrière) : déco bonus, gratuite. */
    private static boolean isBonusDeco(BlockState s) {
        return s.is(Blocks.LOOM) || s.is(Blocks.COMPARATOR) || s.is(BlockTags.FENCE_GATES);
    }

    private static boolean isGlass(BlockState s) {
        if (s.is(BlockTags.IMPERMEABLE)) return true;
        return path(s).contains("glass");
    }

    private static boolean isWood(BlockState s) {
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)
                || s.is(BlockTags.WOODEN_TRAPDOORS) || s.is(BlockTags.WOODEN_FENCES)
                || s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.CAMPFIRE);
    }

    private static String path(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    }
}
