package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Texturiseur : bloc autonome qui retexture le sol naturel autour de lui avec le mélange de blocs
 * puisé dans son inventaire lié, en cercles concentriques (cf. {@link TexturizerBlockEntity}).
 * Contrairement au contrôleur/à la niveleuse, la zone travaillée démarre SOUS le bloc lui-même
 * (demande explicite) : la position de pose n'a donc pas besoin d'orientation particulière.
 */
public class TexturizerBlock extends Block implements EntityBlock {
    public static final MapCodec<TexturizerBlock> CODEC = simpleCodec(TexturizerBlock::new);

    public TexturizerBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TexturizerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null; // la retexturation ne tourne que côté serveur
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TexturizerBlockEntity texturizer) {
                texturizer.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TexturizerBlockEntity be) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Le BlockEntity EST le conteneur (pioche, cf. {@link TexturizerBlockEntity}) : sans cette
     * surcharge, casser le bloc perdrait silencieusement l'outil qu'il contenait. On le fait tomber
     * avant la destruction du bloc.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock() && level.getBlockEntity(pos) instanceof TexturizerBlockEntity be) {
            Containers.dropContents(level, pos, be);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        TooltipHelper.machine(tooltip, getDescriptionId(), 2);
    }
}
