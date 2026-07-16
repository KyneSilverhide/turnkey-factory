package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlockEntity;
import dev.aurelien.prefab.build.RoofType;
import dev.aurelien.prefab.build.Theme;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Choix de style depuis le GUI : thème de matériaux + forme de toit (ordinaux). */
public record SetStylePayload(BlockPos pos, int theme, int roof) implements CustomPacketPayload {
    public static final Type<SetStylePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_style"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetStylePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetStylePayload::pos,
            ByteBufCodecs.VAR_INT, SetStylePayload::theme,
            ByteBufCodecs.VAR_INT, SetStylePayload::roof,
            SetStylePayload::new
    );

    @Override
    public Type<SetStylePayload> type() {
        return TYPE;
    }

    public static void handle(SetStylePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof ControllerBlockEntity be) {
                be.setStyle(Theme.byOrdinal(p.theme()), RoofType.byOrdinal(p.roof()));
            }
        });
    }
}
