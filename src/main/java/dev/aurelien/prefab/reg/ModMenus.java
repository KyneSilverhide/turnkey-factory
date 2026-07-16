package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.menu.ControllerMenu;
import dev.aurelien.prefab.menu.LevelerMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, PrefabMod.MODID);

    public static final Supplier<MenuType<ControllerMenu>> CONTROLLER =
            MENUS.register("controller", () -> IMenuTypeExtension.create(ControllerMenu::new));

    public static final Supplier<MenuType<LevelerMenu>> LEVELER =
            MENUS.register("leveler", () -> IMenuTypeExtension.create(LevelerMenu::new));
}
