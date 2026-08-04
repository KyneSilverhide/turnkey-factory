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

/** Bascule l'option « parcelles de terre grossière » (case à cocher de l'interface). */
public record SetTexturizerCoarseDirtPayload(BlockPos pos, boolean enabled) implements CustomPacketPayload {
    public static final Type<SetTexturizerCoarseDirtPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_texturizer_coarse_dirt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTexturizerCoarseDirtPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTexturizerCoarseDirtPayload::pos,
            ByteBufCodecs.BOOL, SetTexturizerCoarseDirtPayload::enabled,
            SetTexturizerCoarseDirtPayload::new
    );

    @Override
    public Type<SetTexturizerCoarseDirtPayload> type() {
        return TYPE;
    }

    public static void handle(SetTexturizerCoarseDirtPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof TexturizerBlockEntity be) {
                be.setCoarseDirtPatches(p.enabled());
            }
        });
    }
}
