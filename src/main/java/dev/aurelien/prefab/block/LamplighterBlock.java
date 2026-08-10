package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * Allumeur de réverbères : plante des lampadaires autour de lui sur une grille espacée, en restant
 * au sol (cf. {@link LamplighterBlockEntity}). Comme le texturiseur, la zone travaillée démarre sous
 * le bloc lui-même : aucune orientation particulière n'est nécessaire à la pose.
 */
public class LamplighterBlock extends Block implements EntityBlock {
    public static final MapCodec<LamplighterBlock> CODEC = simpleCodec(LamplighterBlock::new);

    public LamplighterBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LamplighterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null; // la pose de lampadaires ne tourne que côté serveur
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof LamplighterBlockEntity lamplighter) {
                lamplighter.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LamplighterBlockEntity be) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
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
