package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.StarterHouseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bouton « Construire » du kit de maison de départ : pose la maison et consomme le bloc. */
public record StarterHouseBuildPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<StarterHouseBuildPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "starter_house_build"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StarterHouseBuildPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StarterHouseBuildPayload::pos,
            StarterHouseBuildPayload::new
    );

    @Override
    public Type<StarterHouseBuildPayload> type() {
        return TYPE;
    }

    public static void handle(StarterHouseBuildPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (!(ctx.player().level() instanceof ServerLevel level)) return;
            if (!(level.getBlockEntity(p.pos()) instanceof StarterHouseBlockEntity be)) return;

            if (!be.build(level)) return;

            // Fermer APRÈS la pose : le bloc n'a été retiré que si build() a réussi, et un menu
            // adossé à un BlockEntity disparu se ferait de toute façon invalider à la volée
            // (stillValid). Sur l'échec (schéma introuvable), le bloc et le menu restent en place.
            ctx.player().closeContainer();
        });
    }
}
