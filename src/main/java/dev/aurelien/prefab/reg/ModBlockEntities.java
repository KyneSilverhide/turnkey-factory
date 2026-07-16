package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlockEntity;
import dev.aurelien.prefab.block.LevelerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, PrefabMod.MODID);

    public static final Supplier<BlockEntityType<ControllerBlockEntity>> CONTROLLER =
            BLOCK_ENTITIES.register("controller", () ->
                    BlockEntityType.Builder.of(ControllerBlockEntity::new, ModBlocks.CONTROLLER.get()).build(null));

    public static final Supplier<BlockEntityType<LevelerBlockEntity>> LEVELER =
            BLOCK_ENTITIES.register("leveler", () ->
                    BlockEntityType.Builder.of(LevelerBlockEntity::new, ModBlocks.LEVELER.get()).build(null));
}
