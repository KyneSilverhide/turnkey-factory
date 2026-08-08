package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlockEntity;
import dev.aurelien.prefab.block.LamplighterBlockEntity;
import dev.aurelien.prefab.block.LevelerBlockEntity;
import dev.aurelien.prefab.block.TexturizerBlockEntity;
import dev.aurelien.prefab.block.TurretBaseBlockEntity;
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

    public static final Supplier<BlockEntityType<TexturizerBlockEntity>> TEXTURIZER =
            BLOCK_ENTITIES.register("texturizer", () ->
                    BlockEntityType.Builder.of(TexturizerBlockEntity::new, ModBlocks.TEXTURIZER.get()).build(null));

    public static final Supplier<BlockEntityType<LamplighterBlockEntity>> LAMPLIGHTER =
            BLOCK_ENTITIES.register("lamplighter", () ->
                    BlockEntityType.Builder.of(LamplighterBlockEntity::new, ModBlocks.LAMPLIGHTER.get()).build(null));

    public static final Supplier<BlockEntityType<TurretBaseBlockEntity>> TURRET_BASE =
            BLOCK_ENTITIES.register("turret_base", () ->
                    BlockEntityType.Builder.of(TurretBaseBlockEntity::new, ModBlocks.TURRET_BASE.get()).build(null));
}
