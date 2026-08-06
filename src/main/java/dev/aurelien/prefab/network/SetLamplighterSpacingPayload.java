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

public record SetLamplighterSpacingPayload(BlockPos pos, int spacing) implements CustomPacketPayload {
    public static final Type<SetLamplighterSpacingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_lamplighter_spacing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLamplighterSpacingPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLamplighterSpacingPayload::pos,
            ByteBufCodecs.VAR_INT, SetLamplighterSpacingPayload::spacing,
            SetLamplighterSpacingPayload::new
    );

    @Override
    public Type<SetLamplighterSpacingPayload> type() {
        return TYPE;
    }

    public static void handle(SetLamplighterSpacingPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof LamplighterBlockEntity be) {
                be.setSpacing(p.spacing());
            }
        });
    }
}
