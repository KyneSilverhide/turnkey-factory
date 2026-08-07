package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * Tourelle défensive : cible et attaque automatiquement les entités dans un rayon donné, selon
 * les catégories activées dans l'interface (cf. {@link TurretBlockEntity}). Le socle rend en 3D
 * via le modèle de bloc statique ; le canon mobile (visée) est dessiné par un
 * {@code BlockEntityRenderer} par-dessus. Activée par signal redstone (pas de bouton Marche/Arrêt
 * dans le GUI) — cf. {@link ITurret#syncRedstoneState}.
 */
public class TurretBlock extends Block implements EntityBlock {
    public static final MapCodec<TurretBlock> CODEC = simpleCodec(TurretBlock::new);

    public TurretBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null; // le ciblage/tir ne tourne que côté serveur
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TurretBlockEntity turret) {
                turret.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretBlockEntity be) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            ITurret.syncRedstoneState(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) {
            ITurret.syncRedstoneState(level, pos);
        }
    }
}
