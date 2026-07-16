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

public record SetOffsetPayload(BlockPos pos, int ox, int oy, int oz) implements CustomPacketPayload {
    public static final Type<SetOffsetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_offset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetOffsetPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetOffsetPayload::pos,
            ByteBufCodecs.VAR_INT, SetOffsetPayload::ox,
            ByteBufCodecs.VAR_INT, SetOffsetPayload::oy,
            ByteBufCodecs.VAR_INT, SetOffsetPayload::oz,
            SetOffsetPayload::new
    );

    @Override
    public Type<SetOffsetPayload> type() {
        return TYPE;
    }

    public static void handle(SetOffsetPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof ControllerBlockEntity be) {
                be.setOffset(p.ox(), p.oy(), p.oz());
            }
        });
    }
}
