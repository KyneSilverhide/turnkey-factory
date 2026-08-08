package dev.aurelien.prefab.compat.create;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.aurelien.prefab.PrefabMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Bloc/item/BlockEntityType de la tourelle Create — n'existe que si Create est chargé
 * ({@link dev.aurelien.prefab.compat.CreateCompat#isLoaded()}). Toute cette classe (y compris ses
 * champs statiques {@code DeferredRegister}) ne doit être touchée que depuis un appel gardé par
 * cette vérification : {@link #register} et {@link #onCommonSetup} sont les deux seuls points
 * d'entrée, câblés depuis {@code PrefabMod} sous garde. Tant qu'aucun code non gardé (ex.
 * {@code ModBlocks}, {@code PrefabModClient}) ne référence cette classe, le classloader ne tente
 * jamais de la charger — donc jamais de résolution de {@code KineticBlock}/{@code KineticBlockEntity}
 * — quand Create est absent.
 */
public final class CreateKineticContent {
    private CreateKineticContent() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PrefabMod.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, PrefabMod.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PrefabMod.MODID);

    public static final DeferredBlock<TurretCreateBlock> TURRET_CREATE = BLOCKS.registerBlock(
            "turret_create",
            TurretCreateBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
    );

    public static final Supplier<BlockEntityType<TurretCreateBlockEntity>> TURRET_CREATE_BE = BLOCK_ENTITIES.register(
            "turret_create",
            () -> BlockEntityType.Builder.of(TurretCreateBlockEntity::new, TURRET_CREATE.get()).build(null)
    );

    public static final DeferredItem<BlockItem> TURRET_CREATE_ITEM =
            ITEMS.registerSimpleBlockItem("turret_create", TURRET_CREATE);

    /**
     * Coût en stress (SU) de référence, enregistré au catalogue Create (lunettes d'ingénieur…) — même
     * ordre de grandeur qu'une petite machine Create. En jeu, le coût réel appliqué par
     * {@link TurretCreateBlockEntity#calculateStressApplied} varie avec la portée configurée ; cette
     * valeur catalogue reste exacte pour la portée par défaut ({@link TurretCombat#DEFAULT_RANGE}),
     * d'où le partage de cette constante plutôt que deux nombres qui pourraient diverger.
     */
    static final double STRESS_IMPACT = 4.0;

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockStressValues.IMPACTS.register(TURRET_CREATE.get(), () -> STRESS_IMPACT));
    }
}
