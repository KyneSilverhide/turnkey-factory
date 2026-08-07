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

/** Les 3 cases à cocher (Hostile/Neutre/Joueur) dans un seul paquet plutôt que trois. */
public record SetTurretTargetsPayload(BlockPos pos, boolean hostile, boolean neutral, boolean player) implements CustomPacketPayload {
    public static final Type<SetTurretTargetsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "set_turret_targets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTurretTargetsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetTurretTargetsPayload::pos,
            ByteBufCodecs.BOOL, SetTurretTargetsPayload::hostile,
            ByteBufCodecs.BOOL, SetTurretTargetsPayload::neutral,
            ByteBufCodecs.BOOL, SetTurretTargetsPayload::player,
            SetTurretTargetsPayload::new
    );

    @Override
    public Type<SetTurretTargetsPayload> type() {
        return TYPE;
    }

    public static void handle(SetTurretTargetsPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof ITurret be) {
                be.setTargets(p.hostile(), p.neutral(), p.player());
            }
        });
    }
}
