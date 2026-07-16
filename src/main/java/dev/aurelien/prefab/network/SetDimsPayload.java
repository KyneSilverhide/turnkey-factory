package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetDimsPayload(BlockPos pos, int w, int l, int h) implements CustomPacketPayload {
    public static final Type<SetDimsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_dims"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDimsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetDimsPayload::pos,
            ByteBufCodecs.VAR_INT, SetDimsPayload::w,
            ByteBufCodecs.VAR_INT, SetDimsPayload::l,
            ByteBufCodecs.VAR_INT, SetDimsPayload::h,
            SetDimsPayload::new
    );

    @Override
    public Type<SetDimsPayload> type() {
        return TYPE;
    }

    public static void handle(SetDimsPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof ControllerBlockEntity be) {
                be.setDims(p.w(), p.l(), p.h());
            }
        });
    }
}
