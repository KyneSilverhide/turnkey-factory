package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ITurret;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetTurretRangePayload(BlockPos pos, int range) implements CustomPacketPayload {
    public static final Type<SetTurretRangePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_turret_range"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTurretRangePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTurretRangePayload::pos,
            ByteBufCodecs.VAR_INT, SetTurretRangePayload::range,
            SetTurretRangePayload::new
    );

    @Override
    public Type<SetTurretRangePayload> type() {
        return TYPE;
    }

    public static void handle(SetTurretRangePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof ITurret be) {
                be.setRange(p.range());
            }
        });
    }
}
