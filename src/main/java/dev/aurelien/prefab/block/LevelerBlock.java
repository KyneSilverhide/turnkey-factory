package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.ChatFormatting;
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
 * Niveleuse : bloc autonome qui aplanit le terrain à l'aide d'une pelle placée dans son interface
 * (durabilité consommée par bloc retiré/posé). La zone travaillée est un carré centré sur le bloc
 * lui-même (portée réglable, même convention que le texturiseur/l'allumeur de réverbères) ; sa
 * propre colonne est toujours exclue du plan (cf. {@link LevelerBlockEntity#computePlan}), pour ne
 * jamais se recouvrir ni s'enterrer elle-même quand la hauteur cible change.
 */
public class LevelerBlock extends Block implements EntityBlock {
    public static final MapCodec<LevelerBlock> CODEC = simpleCodec(LevelerBlock::new);

    public LevelerBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LevelerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null; // le nivellement ne tourne que côté serveur
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof LevelerBlockEntity leveler) {
                leveler.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LevelerBlockEntity be) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Le BlockEntity EST le conteneur (pelle + pioche, cf. {@link LevelerBlockEntity}) : sans ce
     * surcharge, casser le bloc perd silencieusement les outils qu'il contenait (contrairement à un
     * coffre/four vanilla, qui les éjectent toujours). On les fait tomber avant la destruction du bloc.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock() && level.getBlockEntity(pos) instanceof LevelerBlockEntity be) {
            Containers.dropContents(level, pos, be);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String id = getDescriptionId();
        TooltipHelper.machine(tooltip, id,
                Component.translatable(id + ".tooltip.req_1").withStyle(ChatFormatting.GRAY),
                Component.translatable(id + ".tooltip.req_2").withStyle(ChatFormatting.GRAY));
    }
}
