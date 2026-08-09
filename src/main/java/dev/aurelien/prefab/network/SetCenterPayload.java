package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.CenterableMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bouton "Centre" : désigne le bloc {@code pos} comme référence géométrique pour ses voisins directs (cf. CenterableMachine). */
public record SetCenterPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<SetCenterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_center"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCenterPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetCenterPayload::pos,
            SetCenterPayload::new
    );

    @Override
    public Type<SetCenterPayload> type() {
        return TYPE;
    }

    public static void handle(SetCenterPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof CenterableMachine be) {
                be.setAsCenter();
            }
        });
    }
}
