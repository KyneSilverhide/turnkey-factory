package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Lance-flammes : l'arme qui brûle la lave du réservoir du socle ({@link TurretTank}) au lieu de
 * puiser des pépites dans les inventaires liés. Dégâts plus faibles que la mitrailleuse, mais la
 * cible ressort enflammée et ralentie — une arme de zone de contrôle plutôt que de démolition.
 * <p>
 * <strong>Elle n'a besoin d'aucun coffre</strong> ({@link #needsLinkedInventory} = {@code false}) :
 * un socle cinétique alimenté par tuyau est une tourelle complète à lui tout seul. Le socle à
 * charbon, lui, continue d'avoir besoin d'un inventaire lié — pour son charbon, pas pour l'arme.
 * <p>
 * Les <em>dégâts</em> restent de type {@code magic} comme ceux de la mitrailleuse, et pas
 * {@code inFire} : les dégâts de feu sont purement et simplement annulés sur les blazes, cubes de
 * magma et squelettes wither, ce qui rendrait cette arme-ci inutile exactement là où un joueur
 * s'attend à la sortir. Le feu est l'effet ({@link #IGNITE_SECONDS}), pas le vecteur.
 */
public class TurretFlamethrowerBlock extends TurretWeaponBlock {
    public static final MapCodec<TurretFlamethrowerBlock> CODEC = simpleCodec(TurretFlamethrowerBlock::new);

    /**
     * 125 mB par tir, soit <strong>8 tirs par seau</strong> et 64 par plein. Le chiffre s'aligne
     * exactement sur le socle à charbon, où 1 charbon = 8 tirs (cf.
     * {@code TurretBaseBlockEntity#TICKS_PER_SHOT}) : « une unité de combustible = 8 tirs » est la
     * même règle des deux côtés, au lieu de deux barèmes sans rapport.
     * <p>
     * Conséquence assumée : à la cadence de base un plein tient une minute, et sur un socle Create
     * poussé au maximum il part en six secondes. Le seau à la main est un dépannage ; un
     * lance-flammes qui tourne vraiment veut un tuyau.
     */
    public static final int LAVA_PER_SHOT = 125;

    /** 2.0 contre 3.0 pour la mitrailleuse à munition pleine : l'embrasement paie la différence. */
    private static final float BASE_DAMAGE = 2.0f;
    /** Même durée que l'obus incendiaire — c'est le même feu, il n'y a pas de raison qu'il diffère. */
    private static final float IGNITE_SECONDS = 5f;
    /**
     * Lenteur II, volontairement <strong>courte</strong> et réappliquée à chaque tir plutôt que
     * longue : réappliquer un effet de même amplificateur ne rafraîchit sa durée que si la nouvelle
     * est plus longue, donc un ralentissement de 10 s se verrouillerait dès le premier tir et
     * survivrait à la fuite de la cible. À 3 s, il ne tient que tant que la tourelle tire vraiment.
     */
    private static final int SLOWNESS_TICKS = 60;
    private static final int SLOWNESS_AMPLIFIER = 1;

    // Le jet : nombre de tranches le long de la ligne de tir, flammes par tranche, demi-largeur du
    // cône à hauteur de la cible (en blocs) et vitesse donnée aux particules le long de l'axe.
    private static final int TRAIL_STEPS = 14;
    private static final int FLAMES_PER_STEP = 3;
    private static final double CONE_HALF_WIDTH = 0.35;
    private static final double FLAME_SPEED = 0.06;

    public TurretFlamethrowerBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public float baseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    public int tankCostPerShot() {
        return LAVA_PER_SHOT;
    }

    @Override
    public boolean hasAmmo(ServerLevel server, ITurret turret, List<BlockPos> linked) {
        return turret.tank().has(LAVA_PER_SHOT);
    }

    @Override
    @Nullable
    public Shot consumeShot(ServerLevel server, ITurret turret, List<BlockPos> linked) {
        if (!turret.tank().tryDrain(LAVA_PER_SHOT)) return null;
        return new Shot(BASE_DAMAGE, IGNITE_SECONDS, SLOWNESS_TICKS, SLOWNESS_AMPLIFIER);
    }

    /** Cf. javadoc de classe : alimentée par tuyau, cette arme se passe complètement de coffre. */
    @Override
    public boolean needsLinkedInventory() {
        return false;
    }

    @Override
    public Component ammoStatusKey() {
        return Component.translatable("gui.turnkey_factory.turret.checklist.lava");
    }

    /**
     * Un jet, pas une balle : là où la mitrailleuse aligne douze étincelles sur la ligne de tir
     * exacte, le lance-flammes ouvre un cône qui s'élargit avec la distance et dont les particules
     * partent vers l'avant (comptage à zéro + vecteur de vitesse, la convention vanilla pour une
     * particule dirigée — avec un compte non nul, les trois composantes seraient interprétées comme
     * une dispersion et les flammes resteraient sur place).
     * <p>
     * Le cône s'arrête à la cible et non à la portée configurée : le tir reste du hitscan, et un jet
     * qui traverserait le mob pour finir dans le décor mentirait sur ce qui a été touché.
     */
    @Override
    public void spawnTrail(ServerLevel server, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0e-4) return;

        Vec3 dir = delta.scale(1.0 / length);
        // Base orthonormée autour de l'axe de tir, pour disperser dans le plan perpendiculaire. Le
        // cas dégénéré (tir à la verticale, où le produit vectoriel avec +Y s'annule) prend un axe
        // arbitraire : à la verticale, aucune orientation du cône n'est plus juste qu'une autre.
        Vec3 side = Math.abs(dir.y) > 0.999
                ? new Vec3(1, 0, 0)
                : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = dir.cross(side).normalize();

        RandomSource rng = server.getRandom();
        for (int i = 1; i <= TRAIL_STEPS; i++) {
            double t = (double) i / TRAIL_STEPS;
            Vec3 center = from.add(delta.scale(t));
            double spread = CONE_HALF_WIDTH * t;
            for (int j = 0; j < FLAMES_PER_STEP; j++) {
                Vec3 p = center
                        .add(side.scale((rng.nextDouble() * 2 - 1) * spread))
                        .add(up.scale((rng.nextDouble() * 2 - 1) * spread));
                server.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 0,
                        dir.x * FLAME_SPEED, dir.y * FLAME_SPEED, dir.z * FLAME_SPEED, 1.0);
            }
        }

        // Fumée au bout, gouttes de lave à la bouche : la première dit « ça brûle là-bas », la
        // seconde ancre le jet au canon — sans elle, il semblait naître à un mètre devant l'arme.
        server.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 4, 0.25, 0.25, 0.25, 0.01);
        server.sendParticles(ParticleTypes.LAVA, from.x, from.y, from.z, 1, 0.05, 0.05, 0.05, 0.0);
    }

    /**
     * Le souffle d'un brûleur : l'attaque sèche du blaze, descendue en hauteur pour perdre son côté
     * « créature », par-dessus le ronflement continu du feu. Les deux sons vanilla les plus proches
     * d'une torche à gaz — le duo dispenser/shulker de la mitrailleuse sonnait mécanique, pas chaud.
     */
    @Override
    public void playFireSound(ServerLevel server, BlockPos pos) {
        float pitch = 0.55f + server.getRandom().nextFloat() * 0.15f;
        server.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.5f, pitch);
        server.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0f, 1.3f);
    }

    /**
     * Le carburant ne se devine ni au modèle ni à la recette — c'est la question qu'on se pose en
     * premier. La seconde ligne prévient que casser le socle vide le réservoir : c'est le
     * comportement de <em>toute</em> l'énergie stockée d'un socle (la charge de charbon part déjà de
     * la même façon, les tables de butin ne recopient aucune donnée de BlockEntity), mais huit seaux
     * de lave qui disparaissent se remarquent bien plus qu'un compteur de tirs, et c'est justement
     * en déplaçant sa tourelle qu'on le découvrirait.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.turnkey_factory.turret_flamethrower.tooltip.lava",
                TurretTank.BUCKETS, LAVA_PER_SHOT).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.turnkey_factory.turret_flamethrower.tooltip.drain")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
