package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlock;
import dev.aurelien.prefab.block.LamplighterBlock;
import dev.aurelien.prefab.block.LevelerBlock;
import dev.aurelien.prefab.block.TexturizerBlock;
import dev.aurelien.prefab.block.TurretBaseBlock;
import dev.aurelien.prefab.block.TurretWeaponBlock;
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
}
