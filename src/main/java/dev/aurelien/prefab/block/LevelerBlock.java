package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Niveleuse : bloc autonome qui aplanit le terrain à l'aide d'une pelle placée dans son interface
 * (durabilité consommée par bloc retiré/posé). Comme le bloc de contrôle, la zone travaillée se
 * déploie DEVANT le bloc (sens de pose du joueur), jamais dessous/sur lui : ça évite qu'elle finisse
 * par se recouvrir ou s'enterrer elle-même quand la hauteur cible change.
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof LevelerBlockEntity be) {
            // la zone démarre devant le bloc, dans la direction où regarde le joueur au moment de la pose
            be.setFacing(Direction.fromYRot(placer.getYRot()));
        }
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
}
