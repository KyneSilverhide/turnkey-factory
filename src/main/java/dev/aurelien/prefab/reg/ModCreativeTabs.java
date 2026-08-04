package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Onglet créatif dédié au mod : regroupe tous nos objets/blocs au même endroit. */
public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PrefabMod.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.turnkey_factory.main"))
            .icon(() -> new ItemStack(ModItems.CONTROLLER.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.CONTROLLER.get());
                output.accept(ModItems.LEVELER.get());
                output.accept(ModItems.TEXTURIZER.get());
                output.accept(ModItems.ARCHITECT_BLUEPRINT.get());
                output.accept(ModItems.CONTROL_CORE.get());
            })
            .build());
}
