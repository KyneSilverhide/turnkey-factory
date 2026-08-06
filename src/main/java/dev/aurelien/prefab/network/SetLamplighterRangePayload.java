package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.LamplighterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetLamplighterRangePayload(BlockPos pos, int range) implements CustomPacketPayload {
    public static final Type<SetLamplighterRangePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_lamplighter_range"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLamplighterRangePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLamplighterRangePayload::pos,
            ByteBufCodecs.VAR_INT, SetLamplighterRangePayload::range,
            SetLamplighterRangePayload::new
    );

    @Override
    public Type<SetLamplighterRangePayload> type() {
        return TYPE;
    }

    public static void handle(SetLamplighterRangePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof LamplighterBlockEntity be) {
                be.setRange(p.range());
            }
        });
    }
}
