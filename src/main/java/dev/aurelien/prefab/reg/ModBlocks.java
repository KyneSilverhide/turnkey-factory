package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlock;
import dev.aurelien.prefab.block.LamplighterBlock;
import dev.aurelien.prefab.block.LevelerBlock;
import dev.aurelien.prefab.block.StarterHouseBlock;
import dev.aurelien.prefab.block.TexturizerBlock;
import dev.aurelien.prefab.block.TurretBaseBlock;
import dev.aurelien.prefab.block.TurretFlamethrowerBlock;
import dev.aurelien.prefab.block.TurretWeaponBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PrefabMod.MODID);

    /**
     * Les quatre machines de chantier sont modelées en établis (piètement, plateau débordant, outils
     * posés dessus) et non en cubes pleins : elles ont donc toutes besoin de {@code noOcclusion},
     * sans quoi le moteur élimine les faces des blocs voisins comme si elles étaient cachées et on
     * voit à travers le décor par le vide entre les pieds. La boîte de collision, elle, reste le cube
     * plein par défaut — un plateau à hauteur de taille sur lequel on peut marcher.
     */
    public static final DeferredBlock<ControllerBlock> CONTROLLER = BLOCKS.registerBlock(
            "controller",
            ControllerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<LevelerBlock> LEVELER = BLOCKS.registerBlock(
            "leveler",
            LevelerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(3.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<TexturizerBlock> TEXTURIZER = BLOCKS.registerBlock(
            "texturizer",
            TexturizerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
    );

    /** Seule machine à émettre de la lumière : son modèle porte une lanterne allumée. */
    public static final DeferredBlock<LamplighterBlock> LAMPLIGHTER = BLOCKS.registerBlock(
            "lamplighter",
            LamplighterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .noOcclusion()
                    .lightLevel(state -> 10)
                    .requiresCorrectToolForDrops()
    );

    /**
     * Socle de tourelle à charbon : la machine, cube plein posé au sol (cf. {@link TurretBaseBlock}).
     * Son pendant cinétique vit dans compat/create, sous garde.
     * <p>
     * <strong>Effacé devant le socle cinétique quand Create est chargé</strong> : sa recette
     * ({@code data/turnkey_factory/recipe/turret_base.json}) et son entrée d'onglet créatif (cf.
     * {@code ModCreativeTabs}) sont alors désactivées — deux socles côte à côte, dont l'un ignore
     * purement et simplement le réseau cinétique, n'offraient pas un choix mais un piège. Le bloc
     * et son item restent malgré tout <strong>toujours</strong> enregistrés, pour exactement la
     * raison détaillée dans {@code ModItems#COPPER_NUGGET} : un socle déjà posé (ou en coffre) dans
     * un monde où Create vient d'être ajouté doit continuer d'exister et de fonctionner.
     */
    public static final DeferredBlock<TurretBaseBlock> TURRET_BASE = BLOCKS.registerBlock(
            "turret_base",
            TurretBaseBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
    );

    /**
     * Kit de maison de départ : le seul bloc du mod qui bâtisse d'un coup, sans matériaux ni file
     * d'attente (cf. {@link dev.aurelien.prefab.block.StarterHouseBlockEntity}). En bois plutôt qu'en
     * métal comme les machines — c'est une caisse de chantier, pas un établi — d'où la hache comme
     * outil et non la pioche. {@code noOcclusion} pour la même raison que les machines : son modèle
     * est une caisse cerclée qui n'occupe pas tout son cube.
     */
    public static final DeferredBlock<StarterHouseBlock> STARTER_HOUSE = BLOCKS.registerBlock(
            "starter_house",
            StarterHouseBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .ignitedByLava()
    );

    /**
     * Ossature en bois : composant de la recette du kit ci-dessus, neuf bûches assemblées en pans de
     * bois. Bloc et non simple objet — il se pose, et sert alors de colombage décoratif. Toute essence
     * convient (sa recette accepte le tag {@code minecraft:logs}), le modèle est unique : c'est la
     * même simplification que le lampadaire de l'allumeur de réverbères, qui puise « une bûche » sans
     * distinguer l'essence dans son coût.
     */
    public static final DeferredBlock<Block> WOODEN_FRAME = BLOCKS.registerSimpleBlock(
            "wooden_frame",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .ignitedByLava()
    );

    /**
     * Arme mitrailleuse, à poser sur un socle. {@code noOcclusion} parce qu'elle n'occupe pas tout
     * son cube (elle est entièrement dessinée par le renderer du socle) : sans ça les faces des blocs
     * voisins seraient éliminées comme si elles étaient cachées, et on verrait à travers le décor
     * tout autour de la tourelle.
     */
    public static final DeferredBlock<TurretWeaponBlock> TURRET_MACHINEGUN = BLOCKS.registerBlock(
            "turret_machinegun",
            TurretWeaponBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
    );

    /**
     * Lance-flammes : seconde arme, alimentée par le réservoir de lave du socle
     * ({@link dev.aurelien.prefab.block.TurretTank}) plutôt que par des munitions en coffre. Mêmes
     * propriétés que la mitrailleuse — c'est le même affût, dessiné par le même renderer, avec la
     * même raison d'être {@code noOcclusion}.
     */
    public static final DeferredBlock<TurretFlamethrowerBlock> TURRET_FLAMETHROWER = BLOCKS.registerBlock(
            "turret_flamethrower",
            TurretFlamethrowerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .strength(3.5F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
    );
}
