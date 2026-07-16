package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.LevelerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetLevelerDimsPayload(BlockPos pos, int width, int length) implements CustomPacketPayload {
    public static final Type<SetLevelerDimsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_leveler_dims"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLevelerDimsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLevelerDimsPayload::pos,
            ByteBufCodecs.VAR_INT, SetLevelerDimsPayload::width,
            ByteBufCodecs.VAR_INT, SetLevelerDimsPayload::length,
            SetLevelerDimsPayload::new
    );

    @Override
    public Type<SetLevelerDimsPayload> type() {
        return TYPE;
    }

    public static void handle(SetLevelerDimsPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof LevelerBlockEntity be) {
                be.setDims(p.width(), p.length());
            }
        });
    }
}
