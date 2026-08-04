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

/** Bascule marche/arrêt du texturiseur (bouton Démarrer/Arrêter). */
public record TexturizerActionPayload(BlockPos pos, boolean active) implements CustomPacketPayload {
    public static final Type<TexturizerActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "texturizer_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TexturizerActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TexturizerActionPayload::pos,
            ByteBufCodecs.BOOL, TexturizerActionPayload::active,
            TexturizerActionPayload::new
    );

    @Override
    public Type<TexturizerActionPayload> type() {
        return TYPE;
    }

    public static void handle(TexturizerActionPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof TexturizerBlockEntity be) {
                be.setActive(p.active());
            }
        });
    }
}
