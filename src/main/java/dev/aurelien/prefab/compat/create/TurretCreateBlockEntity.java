package dev.aurelien.prefab.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.aurelien.prefab.block.ITurret;
import dev.aurelien.prefab.block.TurretCombat;
import dev.aurelien.prefab.menu.TurretMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Variante Create de la tourelle : même ciblage/tir que {@code TurretBlockEntity} (délégué à
 * {@link TurretCombat}, cf. sa javadoc), mais alimentée par un vrai réseau cinétique — membre à
 * part entière (arbre attaché depuis le dessous, cf. {@link TurretCreateBlock}), coût en stress
 * enregistré une fois pour toutes dans {@link CreateKineticContent}. Pas de "consommation" par tir
 * comme le charbon : {@link #tryConsumeRotation} ne fait que vérifier que le réseau tourne assez
 * vite au moment de tirer ; le drain de stress lui-même est continu, géré par Create tant que le
 * bloc reste attaché.
 * <p>
 * Persistance/sync via le contrat {@code SmartBlockEntity} (pas {@code BlockEntity} vanilla) :
 * {@code saveAdditional}/{@code getUpdateTag} sont {@code final} chez Create, il faut passer par
 * {@link #write}/{@link #read} et déclencher la sync via {@code notifyUpdate()} — jamais
 * {@code level.sendBlockUpdated(...)}.
 * <p>
 * Le coût en stress de base ({@link CreateKineticContent#STRESS_IMPACT}) est constant tant que le
 * bloc reste attaché au réseau (Create le somme dans {@code KineticNetwork.members}, indépendamment
 * de l'état "actif"/à vide de la tourelle — même une tourelle idle fait tourner l'engrenage). Un tir
 * ajoute par-dessus un pic ponctuel ({@link #FIRE_SPIKE_MULTIPLIER}, pendant {@link
 * #FIRE_SPIKE_TICKS}) : {@link #calculateStressApplied} multiplie la valeur de base tant que
 * {@link #fireSpikeTicksLeft} n'est pas retombé à zéro. Create ne recalcule pas ce coût tout seul à
 * chaque tick (il est mis en cache par membre dans le réseau) : {@link #pushStressUpdate} force la
 * prise en compte immédiate via {@code KineticNetwork#updateStressFor}, exactement comme le fait
 * {@code GeneratingKineticBlockEntity} quand sa vitesse générée change (vérifié par javap, pas de
 * doc officielle pour ce mécanisme).
 */
public class TurretCreateBlockEntity extends KineticBlockEntity implements MenuProvider, ITurret {
    /** Multiplicateur de stress appliqué brièvement à chaque tir, par-dessus le coût de base constant. */
    private static final float FIRE_SPIKE_MULTIPLIER = 3.0f;
    /** Durée du pic en ticks (10 = 0.5s) — nettement plus court que l'intervalle entre deux tirs. */
    private static final int FIRE_SPIKE_TICKS = 10;

    /**
     * Seuils de régime (tr/min) délimitant les paliers de cadence. Alignés sur les valeurs par
     * défaut de Create ({@code mediumSpeed} = 30, {@code fastSpeed} = 100, {@code maxRotationSpeed}
     * = 256, relevées dans {@code CKinetics}), mais <strong>recopiés</strong> plutôt que lus depuis
     * {@code AllConfigs} : cette échelle sert aussi à l'affichage ({@link #powerLabel}), qui tourne
     * côté client alors que la config est côté serveur. Une seule et même table des deux côtés
     * garantit que ce qu'annonce l'écran est exactement ce que fait le serveur — c'est ce que le
     * joueur vérifie, bien plus que la concordance avec le texte des lunettes d'ingénieur.
     * <p>
     * Le palier maximal se déclenche à 200 tr/min et non à 256 pile : {@code getSpeed()} est un
     * flottant issu d'une chaîne de multiplications par les engrenages, viser l'égalité exacte avec
     * le maximum n'est pas fiable, et rater de 0.5 tr/min coûterait un palier entier sans le
     * moindre retour visible.
     */
    private static final float SPEED_MEDIUM = 30f;
    private static final float SPEED_FAST = 100f;
    private static final float SPEED_OVERDRIVE = 200f;

    /**
     * Intervalle entre deux tirs (ticks), indexé par {@link #cadenceTier}. Au palier maximal, 2 ticks =
     * 10 tirs/s, soit un tir quasi continu. L'entrée d'index 0 (« réseau à l'arrêt ») ne sert JAMAIS de
     * cadence de tir réelle — {@link #tryConsumeRotation} bloque déjà le tir avant qu'un intervalle basé
     * sur ce palier ait pu s'écouler ; elle ne fixe que le délai de nouvelle tentative pendant la panne
     * (cf. {@code TurretCombat#tryFire}, « panne d'énergie »). Elle reprend {@link
     * TurretCombat#DEFAULT_FIRE_INTERVAL} par simple commodité (une valeur de repli qui existe déjà),
     * pas parce qu'un réseau arrêté tirerait à 1 tir/s — ne pas la lire comme un vrai palier de cadence.
     */
    private static final int[] FIRE_INTERVAL_BY_TIER = {TurretCombat.DEFAULT_FIRE_INTERVAL, 20, 10, 5, 2};

    private static final String[] CADENCE_KEYS = {
            "gui.turnkey_factory.turret.cadence.none",
            "gui.turnkey_factory.turret.cadence.slow",
            "gui.turnkey_factory.turret.cadence.medium",
            "gui.turnkey_factory.turret.cadence.fast",
            "gui.turnkey_factory.turret.cadence.max",
    };

    private final TurretCombat combat = new TurretCombat(this, this::tryConsumeRotation, this::notifyUpdate,
            this::onFired, this::fireIntervalTicks);
    private int fireSpikeTicksLeft = 0;
    /** Cf. {@link dev.aurelien.prefab.block.TurretBlockEntity#pendingRedstoneSync} pour la raison d'être. */
    private boolean pendingRedstoneSync = true;

    public TurretCreateBlockEntity(BlockPos pos, BlockState state) {
        super(CreateKineticContent.TURRET_CREATE_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Aucun comportement Create déclaratif nécessaire : le ciblage/tir vit dans TurretCombat.
    }

    /** Appelé par le ticker du bloc, côté client et serveur (cf. {@link TurretCreateBlock#getTicker}). */
    public void tick() {
        super.tick();
        if (fireSpikeTicksLeft > 0 && --fireSpikeTicksLeft == 0) {
            pushStressUpdate();
        }
        if (level instanceof ServerLevel server) {
            if (pendingRedstoneSync) {
                pendingRedstoneSync = false;
                ITurret.syncRedstoneState(level, getBlockPos());
            }
            combat.serverTick(server);
        }
    }

    // ----- ITurret -----

    @Override public int range() { return combat.range(); }
    @Override public boolean active() { return combat.active(); }
    @Override public boolean targetHostile() { return combat.targetHostile(); }
    @Override public boolean targetNeutral() { return combat.targetNeutral(); }
    @Override public boolean targetPlayer() { return combat.targetPlayer(); }
    @Override public int currentTargetId() { return combat.currentTargetId(); }
    @Override public void setActive(boolean value) { combat.setActive(value); }
    /** Le stress de base dépend de la portée (cf. {@link #calculateStressApplied}) : un changement de
     *  portée doit donc être immédiatement répercuté au réseau, pas seulement au prochain tir/pic. */
    @Override public void setRange(int r) { combat.setRange(r); pushStressUpdate(); }
    @Override public void setTargets(boolean hostile, boolean neutral, boolean player) { combat.setTargets(hostile, neutral, player); }

    @Override
    public boolean hasPower() {
        return isSpeedRequirementFulfilled();
    }

    @Override
    public boolean hasAmmo() {
        return combat.hasAmmo();
    }

    /** Enregistré une seule fois par {@link TurretCreateBlock#setPlacedBy}. */
    public void setOwner(java.util.UUID id) { combat.setOwner(id); }

    /**
     * Palier de cadence pour un régime donné : 0 = à l'arrêt (ou sous le seuil de fonctionnement),
     * puis 1 à 4 du plus lent au plus rapide. Statique et sans lecture de config, donc utilisable
     * indifféremment côté serveur (cadence réelle) et côté client (libellé) — cf. la javadoc des
     * seuils pour pourquoi c'est une contrainte et pas un détail.
     */
    private static int cadenceTier(float speed) {
        float rpm = Math.abs(speed);
        if (rpm >= SPEED_OVERDRIVE) return 4;
        if (rpm >= SPEED_FAST) return 3;
        if (rpm >= SPEED_MEDIUM) return 2;
        if (rpm >= 1f) return 1;
        return 0;
    }

    /** Passé à {@link TurretCombat} : plus le réseau tourne vite, plus la tourelle tire vite. */
    private int fireIntervalTicks() {
        return FIRE_INTERVAL_BY_TIER[cadenceTier(getSpeed())];
    }

    @Override
    public Component powerLabel() {
        float speed = getSpeed();
        return Component.translatable("gui.turnkey_factory.turret.rotation",
                Math.round(Math.abs(speed)),
                Component.translatable(CADENCE_KEYS[cadenceTier(speed)]));
    }

    /** 128 rpm = jauge pleine — vitesse "rapide" typique d'un réseau Create correctement démultiplié,
     *  pas un plafond dur (une vitesse plus élevée sature juste la barre à 100%). */
    @Override
    public float powerFraction() {
        return Mth.clamp(Math.abs(getSpeed()) / 128f, 0f, 1f);
    }

    @Override
    public float cogAngle() {
        return KineticBlockEntityRenderer.getAngleForBe(this, getBlockPos(), Direction.Axis.Y);
    }

    /** Passé à {@link TurretCombat#TurretCombat}. Le coût en stress est un drain continu tant que le
     *  bloc est attaché au réseau (enregistré dans {@link CreateKineticContent}) — ici on vérifie
     *  seulement que la vitesse est suffisante au moment du tir, rien à décrémenter. */
    private boolean tryConsumeRotation() {
        return isSpeedRequirementFulfilled();
    }

    /**
     * Coût de base PROPORTIONNEL À LA PORTÉE configurée, plutôt que la valeur catalogue fixe de
     * {@link CreateKineticContent#register} : une tourelle réglée large surveille (et peut engager) un
     * volume bien plus grand, elle doit peser plus lourd sur le réseau. Le facteur est calé pour que la
     * portée par défaut ({@link TurretCombat#DEFAULT_RANGE} = 12) retombe exactement sur l'ancien coût
     * fixe (4.0 SU) — aucune régression pour une tourelle jamais retouchée, seulement un delta au-dessus
     * ou en dessous selon que le joueur agrandit ou réduit la zone. La valeur enregistrée dans le
     * catalogue Create (lunettes d'ingénieur, etc.) reste donc une référence exacte "à portée par
     * défaut", pas une approximation.
     * <p>
     * Multiplie ensuite ce coût de base pendant {@link #fireSpikeTicksLeft} ticks après un tir — cf.
     * javadoc de classe pour pourquoi ce n'est pas pris en compte tout seul par Create.
     */
    private static final double STRESS_PER_RANGE_UNIT = CreateKineticContent.STRESS_IMPACT / TurretCombat.DEFAULT_RANGE;

    @Override
    public float calculateStressApplied() {
        float base = (float) (combat.range() * STRESS_PER_RANGE_UNIT);
        if (fireSpikeTicksLeft > 0) {
            lastStressApplied = base * FIRE_SPIKE_MULTIPLIER;
            return lastStressApplied;
        }
        return base;
    }

    /** Passé comme {@code onFired} à {@link TurretCombat} : lance le pic et le fait prendre en compte
     *  immédiatement (sans ça, le réseau garderait l'ancienne valeur en cache jusqu'au prochain
     *  changement structurel, cf. javadoc de classe). */
    private void onFired() {
        fireSpikeTicksLeft = FIRE_SPIKE_TICKS;
        pushStressUpdate();
    }

    private void pushStressUpdate() {
        if (hasNetwork()) {
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        }
    }

    // ----- Persistance / sync (contrat SmartBlockEntity) -----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        combat.save(tag);
        if (clientPacket) combat.saveTransient(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        combat.load(tag);
        if (clientPacket) combat.loadTransient(tag);
    }

    // ----- Menu -----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.turnkey_factory.turret_create");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TurretMenu(id, inv, getBlockPos());
    }
}
