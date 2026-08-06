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

/** Bascule marche/arrêt de l'allumeur de réverbères (bouton Démarrer/Arrêter). */
public record LamplighterActionPayload(BlockPos pos, boolean active) implements CustomPacketPayload {
    public static final Type<LamplighterActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "lamplighter_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LamplighterActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LamplighterActionPayload::pos,
            ByteBufCodecs.BOOL, LamplighterActionPayload::active,
            LamplighterActionPayload::new
    );

    @Override
    public Type<LamplighterActionPayload> type() {
        return TYPE;
    }

    public static void handle(LamplighterActionPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof LamplighterBlockEntity be) {
                be.setActive(p.active());
            }
        });
    }
}
