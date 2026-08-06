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

public record SetLevelerTargetPayload(BlockPos pos, int targetOffsetY, int fillDepth) implements CustomPacketPayload {
    public static final Type<SetLevelerTargetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_leveler_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLevelerTargetPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLevelerTargetPayload::pos,
            ByteBufCodecs.VAR_INT, SetLevelerTargetPayload::targetOffsetY,
            ByteBufCodecs.VAR_INT, SetLevelerTargetPayload::fillDepth,
            SetLevelerTargetPayload::new
    );

    @Override
    public Type<SetLevelerTargetPayload> type() {
        return TYPE;
    }

    public static void handle(SetLevelerTargetPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof LevelerBlockEntity be) {
                be.setTarget(p.targetOffsetY(), p.fillDepth());
            }
        });
    }
}
