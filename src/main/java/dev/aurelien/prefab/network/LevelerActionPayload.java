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

/** Bascule marche/arrêt de la niveleuse (bouton Démarrer/Arrêter). */
public record LevelerActionPayload(BlockPos pos, boolean active) implements CustomPacketPayload {
    public static final Type<LevelerActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "leveler_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LevelerActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LevelerActionPayload::pos,
            ByteBufCodecs.BOOL, LevelerActionPayload::active,
            LevelerActionPayload::new
    );

    @Override
    public Type<LevelerActionPayload> type() {
        return TYPE;
    }

    public static void handle(LevelerActionPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof LevelerBlockEntity be) {
                be.setActive(p.active());
            }
        });
    }
}
