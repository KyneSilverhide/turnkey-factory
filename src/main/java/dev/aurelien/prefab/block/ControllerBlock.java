package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import dev.aurelien.prefab.compat.CreateCompat;
import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

public class ControllerBlock extends Block implements EntityBlock {
    public static final MapCodec<ControllerBlock> CODEC = simpleCodec(ControllerBlock::new);

    public ControllerBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControllerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            // le fantôme apparaît dans la direction où regarde le joueur au moment de la pose
            be.setFacing(Direction.fromYRot(placer.getYRot()));
        }
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null; // le scan ne tourne que côté serveur
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof ControllerBlockEntity controller) {
                controller.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            // l'écran lit les valeurs courantes directement depuis le BlockEntity client (synchronisé) ;
            // on ne transmet que la position.
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Clic droit AVEC un objet en main :
     * <ul>
     *   <li>livre et plume → on l'écrit en livre des matériaux requis (et de ce qui manque) ;</li>
     *   <li>clipboard Create → on y écrit la même liste (une entrée cochable par matériau) ;</li>
     *   <li>tout autre objet → on ouvre l'interface (comme à main nue).</li>
     * </ul>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.WRITABLE_BOOK)) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
                ItemStack written = be.writeMaterialsBook();
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, written);
                } else if (!player.getInventory().add(written)) {
                    player.drop(written, false);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (CreateCompat.isClipboard(stack)) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
                be.writeMaterialsClipboard(stack);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
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
