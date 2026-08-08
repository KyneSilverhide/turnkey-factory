package dev.aurelien.prefab.compat.create;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import dev.aurelien.prefab.block.ITurret;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
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
 * Variante Create de la tourelle (cf. {@code dev.aurelien.prefab.block.TurretBlock} pour
 * l'équivalent charbon) : n'existe que si Create est chargé, cf. {@link CreateKineticContent} pour
 * les règles d'enregistrement gardé qui rendent ça sûr. Axe fixe vertical, pas de propriété
 * {@code FACING}, d'où {@code KineticBlock} et non {@code DirectionalKineticBlock}. Deux points
 * d'entrée pour la rotation, comme la meule (millstone) :
 * <ul>
 *   <li>un arbre vertical raccordé par le dessous ({@link #hasShaftTowards}, axe Y) ;</li>
 *   <li>{@code implements ICogWheel} sans surcharger {@code isLargeCog()} classe ce bloc comme
 *       "petit engrenage" par défaut (cf. {@code ICogWheel#isSmallCog}) : un grand engrenage (Large
 *       Cogwheel) posé contre n'importe quel côté horizontal s'engrène directement dedans malgré
 *       l'axe perpendiculaire, exactement le mécanisme qu'utilise la meule pour son engrenage
 *       visible en façade (vérifié par javap sur {@code RotationPropagator.getRotationSpeedModifier}
 *       — {@code isLargeToSmallCog}, pas de documentation officielle pour ce mécanisme). Un petit
 *       Cogwheel (non "large") ou un arbre horizontal ne s'y engrènent PAS : seul un Large Cogwheel
 *       le peut.</li>
 * </ul>
 * Ni l'un ni l'autre n'est visuellement évident depuis l'extérieur : {@link #appendHoverText} les
 * rend explicites plutôt que de compter sur l'intuition du joueur.
 */
public class TurretCreateBlock extends KineticBlock implements EntityBlock, ICogWheel {
    public static final MapCodec<TurretCreateBlock> CODEC = simpleCodec(TurretCreateBlock::new);

    public TurretCreateBlock(Properties props) {
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
        return new TurretCreateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof TurretCreateBlockEntity turret) {
                turret.tick();
            }
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.turnkey_factory.turret_create.tooltip.shaft").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.turnkey_factory.turret_create.tooltip.checklist").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretCreateBlockEntity be) {
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
}
