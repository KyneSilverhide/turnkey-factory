package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlock;
import dev.aurelien.prefab.block.LevelerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PrefabMod.MODID);

    public static final DeferredBlock<ControllerBlock> CONTROLLER = BLOCKS.registerBlock(
            "controller",
            ControllerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredBlock<LevelerBlock> LEVELER = BLOCKS.registerBlock(
            "leveler",
            LevelerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
    );
}
