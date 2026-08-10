package dev.aurelien.prefab.compat.create;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import dev.aurelien.prefab.block.ITurret;
import dev.aurelien.prefab.block.ITurretBase;
import dev.aurelien.prefab.block.TurretTank;
import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
 * Socle de tourelle alimenté par un réseau cinétique Create (cf.
 * {@code dev.aurelien.prefab.block.TurretBaseBlock} pour l'équivalent charbon) : n'existe que si
 * Create est chargé, cf. {@link CreateKineticContent} pour les règles d'enregistrement gardé qui
 * rendent ça sûr. Axe fixe vertical, pas de propriété {@code FACING}, d'où {@code KineticBlock} et
 * non {@code DirectionalKineticBlock}.
 * <p>
 * <strong>C'est le socle, et non l'arme, qui est le membre du réseau cinétique</strong> : le
 * raccordement se fait donc au niveau du sol, là où le joueur construit sa transmission. Deux points
 * d'entrée, comme la meule (millstone) :
 * <ul>
 *   <li>un arbre vertical raccordé par le dessous ({@link #hasShaftTowards}, axe Y) ;</li>
 *   <li>{@code implements ICogWheel} sans surcharger {@code isLargeCog()} classe ce bloc comme
 *       "petit engrenage" par défaut (cf. {@code ICogWheel#isSmallCog}) : n'importe quel Cogwheel
 *       (petit OU grand) posé contre un côté horizontal s'engrène directement dedans, exactement le
 *       mécanisme qu'utilise la meule pour son engrenage visible en façade — vérifié par javap sur
 *       {@code RotationPropagator.getRotationSpeedModifier} (pas de documentation officielle pour ce
 *       mécanisme) : la branche "deux petits engrenages adjacents, même axe" existe à part entière
 *       (ratio 1:1, cf. {@code isConnected}) et ne passe PAS par {@code isLargeToSmallCog}, qui ne
 *       gère que le cas où l'un des deux est grand (ratio 2:1). Un arbre horizontal, lui, ne s'y
 *       engrène jamais : {@code Shaft} n'implémente pas {@code ICogWheel}, il ne rentre dans aucune
 *       des deux branches.</li>
 * </ul>
 * Ni l'un ni l'autre n'est visuellement évident depuis l'extérieur : {@link #appendHoverText} les
 * rend explicites plutôt que de compter sur l'intuition du joueur.
 */
public class TurretBaseCreateBlock extends KineticBlock implements EntityBlock, ICogWheel, ITurretBase {
    public static final MapCodec<TurretBaseCreateBlock> CODEC = simpleCodec(TurretBaseCreateBlock::new);

    public TurretBaseCreateBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.SLOW;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretBaseCreateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof TurretBaseCreateBlockEntity turret) {
                turret.tick();
            }
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String id = getDescriptionId();
        TooltipHelper.machine(tooltip, id,
                Component.translatable(id + ".tooltip.req_1").withStyle(ChatFormatting.GRAY),
                Component.translatable(id + ".tooltip.req_2").withStyle(ChatFormatting.GRAY));
    }

    /** Cf. {@link dev.aurelien.prefab.block.TurretBaseBlock#useItemOn} — même remplissage au seau. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (TurretTank.interactWithHeldContainer(stack, level, pos, player, hand, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretBaseCreateBlockEntity be) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    // KineticBlock#onPlace est public (élargi depuis BlockBehaviour) : impossible de restreindre la
    // visibilité en la surchargeant, d'où "public" ici et pas "protected" comme le reste du fichier.
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
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

    /** Enregistre le joueur qui pose la tourelle : jamais ciblé, cf. {@link dev.aurelien.prefab.block.TurretCombat#setOwner}. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof TurretBaseCreateBlockEntity be) {
            be.setOwner(player.getUUID());
        }
    }
}
