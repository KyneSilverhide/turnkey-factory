package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Arme de tourelle : le module qui se pose sur un socle ({@link ITurretBase}) et lui donne sa
 * capacité de tir. Cette classe-ci <strong>est</strong> la mitrailleuse (ses implémentations par
 * défaut décrivent son comportement) ; en ajouter une autre revient à la dériver, redéfinir ce qui
 * diffère, la déclarer dans {@code ModBlocks} et lui donner ses assets — cf.
 * {@link TurretFlamethrowerBlock}. Une arme fonctionne indifféremment sur le socle à charbon et sur
 * le socle cinétique.
 * <p>
 * <strong>Bloc sans état ni BlockEntity</strong> : toute la machine (énergie, redstone, inventaires
 * liés, réservoir, ciblage, réglages, interface) vit dans le socle en dessous, qui est aussi celui
 * qui dessine l'arme — c'est son {@code BlockEntityRenderer} qui rend l'affût et le canon dans ce
 * bloc-ci, d'où {@link RenderShape#INVISIBLE}. Le modèle JSON de l'arme ne sert donc qu'à ses
 * particules de casse. Le seul état propre à une arme, ce sont ses caractéristiques, et elles sont
 * dans le <em>type</em> du bloc, pas dans des données à persister.
 *
 * <h2>Ce qu'une arme décide</h2>
 * {@link TurretCombat} garde le ciblage, la ligne de vue et la cadence — identiques pour toutes les
 * armes — et délègue ici tout le reste : d'où viennent les munitions ({@link #hasAmmo} /
 * {@link #consumeShot}), ce que fait le tir ({@link Shot}), à quoi il ressemble
 * ({@link #spawnTrail} / {@link #playFireSound}). Les munitions sont volontairement <em>hors</em> de
 * TurretCombat depuis qu'elles ne viennent plus forcément d'un coffre : la mitrailleuse puise dans
 * les inventaires liés ({@link TurretAmmo}), le lance-flammes dans le réservoir du socle
 * ({@link TurretTank}).
 */
public class TurretWeaponBlock extends Block {
    public static final MapCodec<TurretWeaponBlock> CODEC = simpleCodec(TurretWeaponBlock::new);

    /** Volume de l'affût, canon exclu : il pivote, aucune boîte fixe ne le suivrait honnêtement. */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    /** Nombre d'étincelles du traceur de la mitrailleuse (cf. {@link #spawnTrail}). */
    private static final int TRACER_STEPS = 12;

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

    // ----- Ce qu'une arme décide (cf. javadoc de classe) -----

    /**
     * Un tir déjà « payé » : dégâts finaux (multiplicateur de munition inclus) et effets à appliquer
     * à la cible. Un seul objet plutôt qu'un hook {@code applyHit} à redéfinir — une arme qui veut
     * enflammer ou ralentir remplit deux champs de plus au lieu de réécrire l'application des dégâts,
     * et {@link TurretCombat} reste le seul endroit du mod qui appelle {@code hurt}.
     *
     * @param damage           dégâts finaux
     * @param igniteSeconds    durée d'embrasement de la cible, {@code 0} = aucun
     * @param slownessTicks    durée du ralentissement, {@code 0} = aucun
     * @param slownessAmplifier amplificateur du ralentissement ({@code 0} = Lenteur I)
     */
    public record Shot(float damage, float igniteSeconds, int slownessTicks, int slownessAmplifier) {}

    /**
     * Faux si cette arme ne peut pas tirer faute de munition. Sondage <strong>non destructif</strong> :
     * appelé à la fois pour la case « munitions » de la checklist et juste avant chaque tir, en
     * amont de la vérification d'énergie.
     */
    public boolean hasAmmo(ServerLevel server, ITurret turret, List<BlockPos> linked) {
        return TurretAmmo.hasAny(server, linked);
    }

    /**
     * Prélève une munition et renvoie le profil du tir, ou {@code null} si le prélèvement échoue.
     * Appelé une seule fois par tir effectif, après {@link #hasAmmo} <em>et</em> après le paiement de
     * l'énergie.
     */
    @Nullable
    public Shot consumeShot(ServerLevel server, ITurret turret, List<BlockPos> linked) {
        return TurretAmmo.consume(server, linked, baseDamage());
    }

    /**
     * Vrai si cette arme a besoin d'au moins un inventaire lié pour espérer tirer un jour. Sert
     * uniquement à couper le scan de cibles d'une tourelle qui ne peut de toute façon rien faire
     * (cf. {@code TurretCombat#serverTick}). Une arme à réservoir embarqué répond {@code false} :
     * alimentée par tuyau, elle n'a besoin d'aucun coffre, et la tester sur {@code linked} la
     * rendrait muette en silence.
     */
    public boolean needsLinkedInventory() {
        return true;
    }

    /** Traînée de particules du canon vers l'impact : rend le tir instantané visible sans entité-projectile. */
    public void spawnTrail(ServerLevel server, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        for (int i = 1; i <= TRACER_STEPS; i++) {
            Vec3 p = from.add(delta.scale((double) i / TRACER_STEPS));
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Deux couches : un déclic mécanique bref (le socle qui encaisse le tir) sous un "pew" d'énergie
     * (même famille sonore que les tirs de bulle du Shulker — le son vanilla le plus proche d'un
     * projectile énergétique) — plus convaincant que {@code DISPENSER_DISPENSE} seul, qui sonnait
     * comme un coffre qu'on ouvre. Hauteur légèrement aléatoire pour ne pas répéter identique à
     * chaque tir.
     */
    public void playFireSound(ServerLevel server, BlockPos pos) {
        float pitch = 0.95f + server.getRandom().nextFloat() * 0.2f;
        server.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.6f, 0.7f);
        server.playSound(null, pos, SoundEvents.SHULKER_SHOOT, SoundSource.BLOCKS, 1.0f, pitch);
    }

    /**
     * Libellé de la case « munitions » de la checklist ({@code TurretScreen}). Résolu côté client
     * depuis l'arme montée, lue dans le monde (cf. {@link ITurret#weaponOn}) : le {@code BlockState}
     * de l'arme est déjà répliqué, donc pas un octet de réseau en plus.
     */
    public Component ammoStatusKey() {
        return Component.translatable("gui.turnkey_factory.turret.checklist.ammo");
    }

    /**
     * Lave consommée par tir, en mB, ou {@code 0} si cette arme n'utilise pas le réservoir du socle.
     * Un seul accesseur plutôt qu'un booléen « utilise le réservoir » <em>plus</em> un coût : c'est la
     * même information, et le GUI a besoin des deux à la fois (afficher la jauge, et convertir son
     * contenu en nombre de tirs restants, comme le fait déjà la jauge de charbon).
     */
    public int tankCostPerShot() {
        return 0;
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

    /**
     * Résumé + détail derrière SHIFT (cf. {@link dev.aurelien.prefab.util.TooltipHelper}), résolus
     * via {@link #getDescriptionId()} : chaque sous-classe (mitrailleuse, lance-flammes) obtient donc
     * son propre texte sans que celle-ci ait à savoir laquelle tire. Ni la condition de pose (socle
     * obligatoire) ni la munition ne sont devinables au modèle ou à la recette.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String id = getDescriptionId();
        TooltipHelper.machine(tooltip, id,
                Component.translatable(id + ".tooltip.req_1").withStyle(ChatFormatting.GRAY));
    }
}
