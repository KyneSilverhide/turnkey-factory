package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.TexturizerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetTexturizerRadiusPayload(BlockPos pos, int radius) implements CustomPacketPayload {
    public static final Type<SetTexturizerRadiusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_texturizer_radius"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTexturizerRadiusPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTexturizerRadiusPayload::pos,
            ByteBufCodecs.VAR_INT, SetTexturizerRadiusPayload::radius,
            SetTexturizerRadiusPayload::new
    );

    @Override
    public Type<SetTexturizerRadiusPayload> type() {
        return TYPE;
    }

    public static void handle(SetTexturizerRadiusPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof TexturizerBlockEntity be) {
                be.setRadius(p.radius());
            }
        });
    }
}
