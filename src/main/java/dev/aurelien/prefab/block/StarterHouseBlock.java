package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Kit de maison de départ : un clic droit ouvre une interface à un seul bouton qui matérialise
 * d'un coup la maison complète (cf. {@link StarterHouseBlockEntity}). Contrairement aux autres
 * machines du mod, il n'y a rien à régler et rien à alimenter — le bloc est consommé par sa
 * propre construction.
 * <p>
 * Seul bloc du mod orienté : {@link #FACING} décide de la rotation appliquée au schéma. Le
 * schéma est dessiné porte au nord, et {@code FACING} pointe du bloc VERS le joueur qui l'a
 * posé (convention du four/coffre vanilla), si bien que la porte tombe toujours face à lui.
 * <p>
 * Aucun ticker : le bloc ne calcule rien tant qu'on n'a pas appuyé sur le bouton, et son
 * BlockEntity n'existe que pour porter le menu et se faire trouver par le rendu du fantôme.
 */
public class StarterHouseBlock extends Block implements EntityBlock {
    public static final MapCodec<StarterHouseBlock> CODEC = simpleCodec(StarterHouseBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StarterHouseBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Opposé du regard : le bloc « fait face » au joueur, donc la porte aussi.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarterHouseBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StarterHouseBlockEntity be) {
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
