package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
 * Socle de tourelle à charbon : la machine complète (énergie, signal redstone, inventaires liés,
 * ciblage, réglages, interface — cf. {@link TurretBaseBlockEntity}), à laquelle il ne manque qu'une
 * arme posée dessus pour tirer ({@link TurretWeaponBlock}). Le socle cinétique de compat/create en
 * est l'équivalent alimenté par un réseau Create ; les deux acceptent indifféremment n'importe
 * quelle arme.
 * <p>
 * <strong>Tout ce que la machine lit dans le monde est lu à la position du socle</strong>, jamais à
 * celle de l'arme : le socle est au sol, c'est contre lui qu'on pose un levier ou un coffre. Activée
 * par signal redstone (pas de bouton Marche/Arrêt dans le GUI) — cf. {@link ITurret#syncRedstoneState}.
 */
public class TurretBaseBlock extends Block implements EntityBlock, ITurretBase {
    public static final MapCodec<TurretBaseBlock> CODEC = simpleCodec(TurretBaseBlock::new);

    public TurretBaseBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretBaseBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null; // le ciblage/tir ne tourne que côté serveur
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TurretBaseBlockEntity turret) {
                turret.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretBaseBlockEntity be) {
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

    /**
     * Enregistre le joueur qui pose le socle : jamais ciblé, cf. {@link TurretCombat#setOwner}.
     * C'est bien le socle et non l'arme qui porte le propriétaire — remplacer une arme usée ne doit
     * pas réattribuer la tourelle à qui passait par là.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof TurretBaseBlockEntity be) {
            be.setOwner(player.getUUID());
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
