package dev.aurelien.prefab;

import dev.aurelien.prefab.client.ControllerScreen;
import dev.aurelien.prefab.client.LevelerScreen;
import dev.aurelien.prefab.client.TexturizerScreen;
import dev.aurelien.prefab.reg.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = PrefabMod.MODID, value = Dist.CLIENT)
public class PrefabModClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONTROLLER.get(), ControllerScreen::new);
        event.register(ModMenus.LEVELER.get(), LevelerScreen::new);
        event.register(ModMenus.TEXTURIZER.get(), TexturizerScreen::new);
    }
}
