package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PrefabMod.MODID);

    public static final DeferredItem<BlockItem> CONTROLLER =
            ITEMS.registerSimpleBlockItem("controller", ModBlocks.CONTROLLER);

    public static final DeferredItem<BlockItem> LEVELER =
            ITEMS.registerSimpleBlockItem("leveler", ModBlocks.LEVELER);

    public static final DeferredItem<BlockItem> TEXTURIZER =
            ITEMS.registerSimpleBlockItem("texturizer", ModBlocks.TEXTURIZER);

    // Composants intermédiaires de la recette du bloc de contrôle.
    /** Plan d'architecte (papier + lapis) : la « mémoire de plan » du bloc de contrôle. */
    public static final DeferredItem<Item> ARCHITECT_BLUEPRINT = ITEMS.registerSimpleItem("architect_blueprint");
    /** Cœur de contrôle (quartz + redstone + or) : la logique de calcul/scan du bloc de contrôle. */
    public static final DeferredItem<Item> CONTROL_CORE = ITEMS.registerSimpleItem("control_core");
}
