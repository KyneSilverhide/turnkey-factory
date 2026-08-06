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

/** Bascule le motif du texturiseur (pierre/terre, ordinal) depuis le GUI. */
public record SetTexturizerPalettePayload(BlockPos pos, int palette) implements CustomPacketPayload {
    public static final Type<SetTexturizerPalettePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_texturizer_palette"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTexturizerPalettePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTexturizerPalettePayload::pos,
            ByteBufCodecs.VAR_INT, SetTexturizerPalettePayload::palette,
            SetTexturizerPalettePayload::new
    );

    @Override
    public Type<SetTexturizerPalettePayload> type() {
        return TYPE;
    }

    public static void handle(SetTexturizerPalettePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof TexturizerBlockEntity be) {
                TexturizerBlockEntity.Palette[] values = TexturizerBlockEntity.Palette.values();
                if (p.palette() >= 0 && p.palette() < values.length) {
                    be.setPalette(values[p.palette()]);
                }
            }
        });
    }
}
