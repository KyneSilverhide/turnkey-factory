package dev.aurelien.prefab.network;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Action de construction : démarrer, annuler, ou démarrer malgré un site obstrué — en écrasant les
 * blocs signalés ({@link #START_FORCE}) ou en les laissant tels quels ({@link #START_IGNORE}).
 */
public record BuildActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int CANCEL = 1;
    public static final int START_FORCE = 2;
    public static final int START_IGNORE = 3;

    public static final Type<BuildActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "build_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BuildActionPayload::pos,
            ByteBufCodecs.VAR_INT, BuildActionPayload::action,
            BuildActionPayload::new
    );

    @Override
    public Type<BuildActionPayload> type() {
        return TYPE;
    }

    public static void handle(BuildActionPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!NetworkUtil.withinReach(ctx.player(), p.pos())) return;
            if (ctx.player().level().getBlockEntity(p.pos()) instanceof ControllerBlockEntity be) {
                boolean creative = ctx.player().isCreative();
                switch (p.action()) {
                    case START -> be.startBuild(creative, ControllerBlockEntity.BuildStartMode.NORMAL);
                    case START_FORCE -> be.startBuild(creative, ControllerBlockEntity.BuildStartMode.FORCE);
                    case START_IGNORE -> be.startBuild(creative, ControllerBlockEntity.BuildStartMode.IGNORE);
                    case CANCEL -> be.cancelBuild();
                }
            }
        });
    }
}
