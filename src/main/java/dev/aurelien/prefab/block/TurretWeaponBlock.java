package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Arme de tourelle : le module qui se pose sur un socle ({@link ITurretBase}) et lui donne sa
 * capacité de tir. Une seule arme existe pour l'instant (la mitrailleuse) ; en ajouter une revient à
 * dériver cette classe, la déclarer dans {@code ModBlocks} et lui donner ses assets — rien d'autre à
 * toucher, et elle fonctionnera indifféremment sur le socle à charbon et sur le socle cinétique.
 * <p>
 * <strong>Bloc sans état ni BlockEntity</strong> : toute la machine (énergie, redstone, inventaires
 * liés, ciblage, réglages, interface) vit dans le socle en dessous, qui est aussi celui qui dessine
 * l'arme — c'est son {@code BlockEntityRenderer} qui rend l'affût et le canon dans ce bloc-ci, d'où
 * {@link RenderShape#INVISIBLE}. Le modèle JSON de l'arme ne sert donc qu'à ses particules de casse.
 * Le seul état propre à une arme, ce sont ses caractéristiques, et elles sont dans le type du bloc
 * (cf. {@link #baseDamage}), pas dans des données à persister.
 */
public class TurretWeaponBlock extends Block {
    public static final MapCodec<TurretWeaponBlock> CODEC = simpleCodec(TurretWeaponBlock::new);

    /** Volume de l'affût, canon exclu : il pivote, aucune boîte fixe ne le suivrait honnêtement. */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    public TurretWeaponBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Dégâts par tir avec une munition pleine (pépite de fer ; le cuivre vaut moitié moins, cf.
     * {@code TurretCombat}). 3.0 et non 4.0 : dégâts de type "magic" (bypasses_armor vanilla), donc
     * déjà non réduits par l'armure de la cible, cumulés à un tir hitscan (ni esquive, ni parade) —
     * 4.0 rendait la tourelle disproportionnée par rapport à son coût de craft. Cf. aussi le coût
     * croissant avec la portée (charge/stress) qui pèse sur la même balance.
     * <p>
     * C'est ici que se différencieront les futures armes : redéfinir cette méthode suffit.
     */
    public float baseDamage() {
        return 3.0f;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Rien à dessiner : le renderer du socle s'en charge (cf. javadoc de classe). */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof ITurretBase;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** Cliquer l'arme ouvre l'interface du socle : pour le joueur c'est une seule machine. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos basePos = pos.below();
        if (!(level.getBlockEntity(basePos) instanceof MenuProvider provider)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.openMenu(provider, buf -> buf.writeBlockPos(basePos));
        }
        return InteractionResult.SUCCESS;
    }

    /** La condition de pose (socle obligatoire) n'est devinable ni au modèle ni à la recette. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.turnkey_factory.turret_weapon.tooltip.base").withStyle(ChatFormatting.GRAY));
    }
}
