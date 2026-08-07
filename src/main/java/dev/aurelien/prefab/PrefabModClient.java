package dev.aurelien.prefab;

import dev.aurelien.prefab.client.ControllerScreen;
import dev.aurelien.prefab.client.LamplighterScreen;
import dev.aurelien.prefab.client.LevelerScreen;
import dev.aurelien.prefab.client.TexturizerScreen;
import dev.aurelien.prefab.client.TurretModel;
import dev.aurelien.prefab.client.TurretRenderer;
import dev.aurelien.prefab.client.TurretScreen;
import dev.aurelien.prefab.compat.CreateCompat;
import dev.aurelien.prefab.compat.create.CreateKineticContent;
import dev.aurelien.prefab.reg.ModBlockEntities;
import dev.aurelien.prefab.reg.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = PrefabMod.MODID, value = Dist.CLIENT)
public class PrefabModClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONTROLLER.get(), ControllerScreen::new);
        event.register(ModMenus.LEVELER.get(), LevelerScreen::new);
        event.register(ModMenus.TEXTURIZER.get(), TexturizerScreen::new);
        event.register(ModMenus.LAMPLIGHTER.get(), LamplighterScreen::new);
        event.register(ModMenus.TURRET.get(), TurretScreen::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TurretModel.LAYER, TurretModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TURRET.get(), TurretRenderer::new);
        // Create-only : cette classe est chargée dans tous les cas (elle sert déjà la tourelle
        // charbon), mais la ligne qui suit ne référence CreateKineticContent (donc KineticBlockEntity)
        // que si la garde passe — résolution paresseuse, même principe que PrefabMod#<init>.
        if (CreateCompat.isLoaded()) {
            event.registerBlockEntityRenderer(CreateKineticContent.TURRET_CREATE_BE.get(), TurretRenderer::new);
        }
    }
}
