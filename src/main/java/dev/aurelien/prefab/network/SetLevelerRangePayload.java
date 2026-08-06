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

public record SetLevelerRangePayload(BlockPos pos, int range) implements CustomPacketPayload {
    public static final Type<SetLevelerRangePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_leveler_range"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLevelerRangePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLevelerRangePayload::pos,
            ByteBufCodecs.VAR_INT, SetLevelerRangePayload::range,
            SetLevelerRangePayload::new
    );

    @Override
    public Type<SetLevelerRangePayload> type() {
        return TYPE;
    }

    public static void handle(SetLevelerRangePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof LevelerBlockEntity be) {
                be.setRange(p.range());
            }
        });
    }
}
