package dev.aurelien.prefab;

import dev.aurelien.prefab.network.BuildActionPayload;
import dev.aurelien.prefab.network.LamplighterActionPayload;
import dev.aurelien.prefab.network.LevelerActionPayload;
import dev.aurelien.prefab.network.SetDimsPayload;
import dev.aurelien.prefab.network.SetLamplighterRangePayload;
import dev.aurelien.prefab.network.SetLamplighterSpacingPayload;
import dev.aurelien.prefab.network.SetLevelerRangePayload;
import dev.aurelien.prefab.network.SetLevelerTargetPayload;
import dev.aurelien.prefab.network.SetCenterPayload;
import dev.aurelien.prefab.network.SetOffsetPayload;
import dev.aurelien.prefab.network.SetStylePayload;
import dev.aurelien.prefab.network.StarterHouseBuildPayload;
import dev.aurelien.prefab.network.SetTexturizerCoarseDirtPayload;
import dev.aurelien.prefab.network.SetTexturizerPalettePayload;
import dev.aurelien.prefab.network.SetTexturizerRadiusPayload;
import dev.aurelien.prefab.network.TexturizerActionPayload;
import dev.aurelien.prefab.network.SetTurretRangePayload;
import dev.aurelien.prefab.network.SetTurretTargetsPayload;
import dev.aurelien.prefab.compat.CreateCompat;
import dev.aurelien.prefab.compat.create.CreateKineticContent;
import dev.aurelien.prefab.reg.ModBlockEntities;
import dev.aurelien.prefab.reg.ModBlocks;
import dev.aurelien.prefab.reg.ModCreativeTabs;
import dev.aurelien.prefab.reg.ModItems;
import dev.aurelien.prefab.reg.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(PrefabMod.MODID)
public class PrefabMod {
    public static final String MODID = "turnkey_factory";

    public PrefabMod(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        // Contenu Create-only : jamais touché si Create est absent (cf. javadoc de CreateKineticContent
        // pour pourquoi cette garde suffit à éviter tout NoClassDefFoundError).
        if (CreateCompat.isLoaded()) {
            CreateKineticContent.register(modBus);
            modBus.addListener(CreateKineticContent::onCommonSetup);
        }

        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::registerCapabilities);
    }

    /**
     * Expose le réservoir de lave des socles de tourelle ({@code TurretTank}) comme un
     * {@code IFluidHandler} de bloc. C'est la capability NeoForge standard : ça suffit à ce qu'un
     * tuyau de Create s'y raccorde, sans une ligne de code spécifique à Create — d'où le fait que le
     * lance-flammes fonctionne à l'identique avec ou sans lui.
     * <p>
     * Enregistrée pour <strong>toutes</strong> les faces (le contexte {@code Direction} est ignoré),
     * y compris {@code null} : un tuyau doit pouvoir arriver par n'importe quel côté, et rien dans un
     * socle ne justifie une entrée privilégiée.
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.TURRET_BASE.get(),
                (be, side) -> be.tank().handler());
        // Même garde que ci-dessus : la ligne ne résout CreateKineticContent (donc KineticBlockEntity)
        // que si Create est chargé.
        if (CreateCompat.isLoaded()) {
            CreateKineticContent.registerCapabilities(event);
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetDimsPayload.TYPE, SetDimsPayload.STREAM_CODEC, SetDimsPayload::handle);
        registrar.playToServer(SetOffsetPayload.TYPE, SetOffsetPayload.STREAM_CODEC, SetOffsetPayload::handle);
        registrar.playToServer(BuildActionPayload.TYPE, BuildActionPayload.STREAM_CODEC, BuildActionPayload::handle);
        registrar.playToServer(SetStylePayload.TYPE, SetStylePayload.STREAM_CODEC, SetStylePayload::handle);
        registrar.playToServer(SetLevelerRangePayload.TYPE, SetLevelerRangePayload.STREAM_CODEC, SetLevelerRangePayload::handle);
        registrar.playToServer(SetLevelerTargetPayload.TYPE, SetLevelerTargetPayload.STREAM_CODEC, SetLevelerTargetPayload::handle);
        registrar.playToServer(LevelerActionPayload.TYPE, LevelerActionPayload.STREAM_CODEC, LevelerActionPayload::handle);
        registrar.playToServer(SetTexturizerRadiusPayload.TYPE, SetTexturizerRadiusPayload.STREAM_CODEC, SetTexturizerRadiusPayload::handle);
        registrar.playToServer(SetTexturizerCoarseDirtPayload.TYPE, SetTexturizerCoarseDirtPayload.STREAM_CODEC, SetTexturizerCoarseDirtPayload::handle);
        registrar.playToServer(SetTexturizerPalettePayload.TYPE, SetTexturizerPalettePayload.STREAM_CODEC, SetTexturizerPalettePayload::handle);
        registrar.playToServer(TexturizerActionPayload.TYPE, TexturizerActionPayload.STREAM_CODEC, TexturizerActionPayload::handle);
        registrar.playToServer(SetLamplighterRangePayload.TYPE, SetLamplighterRangePayload.STREAM_CODEC, SetLamplighterRangePayload::handle);
        registrar.playToServer(SetLamplighterSpacingPayload.TYPE, SetLamplighterSpacingPayload.STREAM_CODEC, SetLamplighterSpacingPayload::handle);
        registrar.playToServer(LamplighterActionPayload.TYPE, LamplighterActionPayload.STREAM_CODEC, LamplighterActionPayload::handle);
        registrar.playToServer(SetTurretRangePayload.TYPE, SetTurretRangePayload.STREAM_CODEC, SetTurretRangePayload::handle);
        registrar.playToServer(SetTurretTargetsPayload.TYPE, SetTurretTargetsPayload.STREAM_CODEC, SetTurretTargetsPayload::handle);
        registrar.playToServer(SetCenterPayload.TYPE, SetCenterPayload.STREAM_CODEC, SetCenterPayload::handle);
        registrar.playToServer(StarterHouseBuildPayload.TYPE, StarterHouseBuildPayload.STREAM_CODEC, StarterHouseBuildPayload::handle);
    }
}
