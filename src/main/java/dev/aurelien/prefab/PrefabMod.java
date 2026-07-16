package dev.aurelien.prefab;

import dev.aurelien.prefab.network.BuildActionPayload;
import dev.aurelien.prefab.network.LevelerActionPayload;
import dev.aurelien.prefab.network.SetDimsPayload;
import dev.aurelien.prefab.network.SetLevelerDimsPayload;
import dev.aurelien.prefab.network.SetLevelerTargetPayload;
import dev.aurelien.prefab.network.SetOffsetPayload;
import dev.aurelien.prefab.network.SetStylePayload;
import dev.aurelien.prefab.reg.ModBlockEntities;
import dev.aurelien.prefab.reg.ModBlocks;
import dev.aurelien.prefab.reg.ModCreativeTabs;
import dev.aurelien.prefab.reg.ModItems;
import dev.aurelien.prefab.reg.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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

        modBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetDimsPayload.TYPE, SetDimsPayload.STREAM_CODEC, SetDimsPayload::handle);
        registrar.playToServer(SetOffsetPayload.TYPE, SetOffsetPayload.STREAM_CODEC, SetOffsetPayload::handle);
        registrar.playToServer(BuildActionPayload.TYPE, BuildActionPayload.STREAM_CODEC, BuildActionPayload::handle);
        registrar.playToServer(SetStylePayload.TYPE, SetStylePayload.STREAM_CODEC, SetStylePayload::handle);
        registrar.playToServer(SetLevelerDimsPayload.TYPE, SetLevelerDimsPayload.STREAM_CODEC, SetLevelerDimsPayload::handle);
        registrar.playToServer(SetLevelerTargetPayload.TYPE, SetLevelerTargetPayload.STREAM_CODEC, SetLevelerTargetPayload::handle);
        registrar.playToServer(LevelerActionPayload.TYPE, LevelerActionPayload.STREAM_CODEC, LevelerActionPayload::handle);
    }
}
