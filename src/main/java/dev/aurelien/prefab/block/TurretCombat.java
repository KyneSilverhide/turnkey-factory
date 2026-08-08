package dev.aurelien.prefab.block;

import dev.aurelien.prefab.build.InventoryNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Ciblage et tir de la tourelle, extrait de {@link TurretBlockEntity} pour être partagé avec
 * l'implémentation Create (compat/create) — les deux BlockEntity ne peuvent pas partager de classe
 * mère (l'une étend {@code BlockEntity}, l'autre {@code KineticBlockEntity}), donc composition
 * plutôt qu'héritage. Le coût en énergie (charbon consommé, ou vitesse de rotation suffisante) est
 * entièrement délégué au propriétaire via {@code tryConsumePower} : {@link #tryFire} l'appelle une
 * seule fois, juste avant d'infliger les dégâts, et annule le tir (sans perdre la cible) s'il
 * renvoie {@code false}. {@code onFired} est un second hook, appelé seulement après un tir
 * effectivement réalisé (pas à chaque tentative) — utilisé par la variante Create pour déclencher un
 * pic de stress ponctuel, sans concept équivalent côté charbon (no-op là-bas).
 * <p>
 * Munitions : identiques pour les deux variantes (contrairement à l'énergie), donc gérées ici plutôt
 * que déléguées au propriétaire. Une pépite de fer vaut les dégâts de base ; une pépite de cuivre
 * vaut moitié moins. Les deux sont reconnues par tag conventionnel ({@code c:nuggets/iron} /
 * {@link #NUGGETS_COPPER}) et non par item précis, donc la pépite de cuivre de n'importe quel mod
 * fait office de munition, pas seulement celle du nôtre (cf. la javadoc de la constante).
 * Piochées dans les inventaires liés ({@link InventoryNetwork}, même flood-fill que les autres
 * machines) au moment du tir, jamais en avance : pas de jauge à charger, juste 1 nugget consommé par
 * tir réussi. Si aucun nugget n'est disponible, le tir est reporté (cible conservée) — même
 * comportement qu'une panne d'énergie temporaire.
 */
public class TurretCombat {
    public static final int MIN_RANGE = 4;
    public static final int MAX_RANGE = 32;
    public static final int DEFAULT_RANGE = 12;

    /**
     * Hauteur de l'axe du canon au-dessus du bas du bloc, en pixels (1/16 de bloc). Partagée avec
     * {@code TurretModel} (côté client), qui en dérive son pivot de tangage. La constante vit ici,
     * du côté commun, et pas dans le modèle : la dépendance ne peut aller que client → commun, une
     * classe serveur qui référencerait {@code TurretModel} (client-only) casserait sur un serveur
     * dédié.
     */
    public static final float MUZZLE_HEIGHT_PX = 23f;

    /** Cadence de base : un tir par seconde. C'est la cadence fixe de la tourelle à charbon ; la
     *  variante Create fournit la sienne selon la vitesse de rotation (cf. {@code fireIntervalTicks}). */
    public static final int DEFAULT_FIRE_INTERVAL = 20;

    private static final int SCAN_INTERVAL = 10;
    private static final int LINK_SCAN_INTERVAL = 20;
    private static final float DAMAGE = 4.0f;
    private static final int TRACER_STEPS = 12;

    /**
     * Munitions désignées par tag conventionnel et non par item précis : n'importe quelle pépite de
     * cuivre est acceptée, quel que soit le mod qui la fournit (Create en ajoute une, comme plusieurs
     * autres mods), et la nôtre est déclarée dans le même tag (cf. {@code data/c/tags/item/nuggets/copper.json}).
     * {@code c:nuggets/copper} n'a pas de constante dans {@code Tags.Items} — le cuivre n'a pas de
     * pépite vanilla en 1.21.1 — d'où la clé construite à la main, avec l'identifiant conventionnel
     * que tout le monde utilise.
     * <p>
     * Seule la <em>clé</em> est statique, jamais l'appartenance : les tags sont vides tant que le
     * datapack n'est pas chargé, donc toute lecture doit rester au moment du tick.
     */
    private static final TagKey<Item> NUGGETS_COPPER =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "nuggets/copper"));

    private final BlockEntity owner;
    private final BooleanSupplier tryConsumePower;
    private final Runnable syncToClient;
    private final Runnable onFired;
    private final IntSupplier fireIntervalTicks;
    private final List<BlockPos> linked = new ArrayList<>();

    private int range = DEFAULT_RANGE;
    private boolean active = false;
    private boolean targetHostile = true;
    private boolean targetNeutral = false;
    private boolean targetPlayer = false;

    private int scanCooldown = 0;
    private int fireCooldown = 0;
    private int linkScanCooldown = 0;
    /** -1 = aucune cible. Seule donnée de visée envoyée au client (cf. javadoc de {@link TurretBlockEntity}). */
    private int currentTargetId = -1;
    /** Recalculé à chaque rescan + juste après extraction (cf. {@link #updateHasAmmo}) — pas seulement
     *  au rythme de {@link #LINK_SCAN_INTERVAL}, pour refléter tout de suite le dernier nugget consommé. */
    private boolean hasAmmo = false;

    public TurretCombat(BlockEntity owner, BooleanSupplier tryConsumePower, Runnable syncToClient,
                        Runnable onFired, IntSupplier fireIntervalTicks) {
        this.owner = owner;
        this.tryConsumePower = tryConsumePower;
        this.syncToClient = syncToClient;
        this.onFired = onFired;
        this.fireIntervalTicks = fireIntervalTicks;
    }

    // ----- Configuration -----

    public int range() { return range; }
    public boolean active() { return active; }
    public boolean targetHostile() { return targetHostile; }
    public boolean targetNeutral() { return targetNeutral; }
    public boolean targetPlayer() { return targetPlayer; }
    public int currentTargetId() { return currentTargetId; }

    public void setActive(boolean value) {
        this.active = value;
        if (!value) setCurrentTarget(null);
        syncToClient.run();
    }

    public void setRange(int r) {
        this.range = clampRange(r);
        syncToClient.run();
    }

    public void setTargets(boolean hostile, boolean neutral, boolean player) {
        this.targetHostile = hostile;
        this.targetNeutral = neutral;
        this.targetPlayer = player;
        syncToClient.run();
    }

    public static int clampRange(int v) {
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, v));
    }

    // ----- Tick serveur -----

    public void serverTick(ServerLevel server) {
        // Le scan des inventaires liés tourne même à l'arrêt (redstone coupée) : les munitions sont
        // prêtes dès la réactivation, même logique que le ravitaillement en charbon côté TurretBlockEntity.
        if (--linkScanCooldown <= 0) {
            linkScanCooldown = LINK_SCAN_INTERVAL;
            boolean changed = InventoryNetwork.rescan(server, owner.getBlockPos(), linked);
            if (updateHasAmmo(server)) changed = true;
            if (changed) syncToClient.run();
        }

        if (!active) {
            return;
        }

        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            acquireTarget(server);
        }

        if (currentTargetId >= 0 && --fireCooldown <= 0) {
            // Relu à chaque tir, jamais mis en cache : côté Create la cadence suit la vitesse du
            // réseau, qui peut changer à tout moment. Plancher à 1 tick — un intervalle nul ou
            // négatif ferait tirer plusieurs fois dans le même tick.
            fireCooldown = Math.max(1, fireIntervalTicks.getAsInt());
            tryFire(server);
        }
    }

    /** Réutilisé par {@link TurretBlockEntity} pour son ravitaillement en charbon, afin de partager
     *  le même flood-fill plutôt que d'en refaire un second en parallèle. */
    public List<BlockPos> linkedInventories() {
        return Collections.unmodifiableList(linked);
    }

    /** État "au moins un nugget disponible", exposé pour la checklist {@code TurretScreen}. */
    public boolean hasAmmo() {
        return hasAmmo;
    }

    private boolean updateHasAmmo(ServerLevel server) {
        boolean now = InventoryNetwork.countEligible(server, linked, TurretCombat::isAmmo) > 0;
        if (now == hasAmmo) return false;
        hasAmmo = now;
        return true;
    }

    /** Cible valide la plus proche dans le rayon configuré, avec ligne de vue dégagée. */
    private void acquireTarget(ServerLevel server) {
        if (!targetHostile && !targetNeutral && !targetPlayer) {
            setCurrentTarget(null);
            return;
        }

        Vec3 origin = muzzlePos();
        AABB area = new AABB(owner.getBlockPos()).inflate(range);
        double rangeSq = (double) range * range;

        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class, area, this::isEligible)) {
            double distSq = candidate.position().distanceToSqr(origin);
            if (distSq > rangeSq || distSq >= bestDistSq) continue;
            if (!hasLineOfSight(server, origin, candidate)) continue;
            best = candidate;
            bestDistSq = distSq;
        }
        setCurrentTarget(best);
    }

    /**
     * Hostile = {@link Enemy} ; Neutre = {@link NeutralMob} vanilla (dôme de fer, enderman,
     * abeille, piglin...) ; Joueur = hors créatif/spectateur. Les animaux apprivoisés ne sont
     * jamais des cibles, quelle que soit la case cochée (une tourelle qui abat le loup du joueur
     * serait un piège, pas une fonctionnalité).
     */
    private boolean isEligible(LivingEntity e) {
        if (!e.isAlive()) return false;
        if (e instanceof TamableAnimal tamable && tamable.isTame()) return false;

        if (e instanceof Player player) {
            return targetPlayer && !player.isSpectator() && !player.getAbilities().invulnerable;
        }
        if (e instanceof Enemy) {
            return targetHostile;
        }
        if (e instanceof NeutralMob) {
            return targetNeutral;
        }
        return false;
    }

    private boolean hasLineOfSight(ServerLevel server, Vec3 origin, LivingEntity target) {
        ClipContext ctx = new ClipContext(origin, target.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty());
        return server.clip(ctx).getType() == HitResult.Type.MISS;
    }

    private void tryFire(ServerLevel server) {
        if (currentTargetId < 0) return;
        Entity entity = server.getEntity(currentTargetId);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            setCurrentTarget(null);
            return;
        }

        Vec3 origin = muzzlePos();
        Vec3 targetPos = target.getEyePosition();
        // Revalidation à l'exécution : la cible a pu sortir de portée/se cacher entre deux scans.
        if (origin.distanceToSqr(targetPos) > (double) range * range || !hasLineOfSight(server, origin, target)) {
            setCurrentTarget(null);
            return;
        }

        // En panne de munitions : même traitement qu'une panne d'énergie, cible conservée.
        Item ammo = InventoryNetwork.pickWeightedRandom(server, linked, TurretCombat::isAmmo, server.getRandom());
        if (ammo == null) {
            return;
        }

        // En panne d'énergie (charbon épuisé ou rotation insuffisante) : la cible reste verrouillée,
        // nouvel essai au prochain FIRE_INTERVAL — pas de tir manqué définitif pour une panne temporaire.
        if (!tryConsumePower.getAsBoolean()) {
            return;
        }

        InventoryNetwork.extract(server, linked, ammo, 1);
        if (updateHasAmmo(server)) syncToClient.run();
        target.hurt(server.damageSources().magic(), damageFor(ammo));
        playFireSound(server);
        spawnTracer(server, origin, targetPos);
        onFired.run();
    }

    private static boolean isAmmo(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(Tags.Items.NUGGETS_IRON) || stack.is(NUGGETS_COPPER);
    }

    /** Pépite de fer = dégâts de base ; pépite de cuivre = moitié moins (cf. javadoc de classe).
     *  Le fer est testé en premier plutôt que le cuivre : un item exotique déclaré dans les deux tags
     *  garde alors les dégâts pleins, au lieu que le résultat dépende de l'ordre des branches. */
    private static float damageFor(Item ammo) {
        return new ItemStack(ammo).is(Tags.Items.NUGGETS_IRON) ? DAMAGE : DAMAGE * 0.5f;
    }

    /**
     * Deux couches : un déclic mécanique bref (le socle qui encaisse le tir) sous un "pew" d'énergie
     * (même famille sonore que les tirs de bulle du Shulker — le son vanilla le plus proche d'un
     * projectile énergétique) — plus convaincant que {@code DISPENSER_DISPENSE} seul, qui sonnait
     * comme un coffre qu'on ouvre. Hauteur légèrement aléatoire pour ne pas répéter identique à
     * chaque tir.
     */
    private void playFireSound(ServerLevel server) {
        float pitch = 0.95f + server.getRandom().nextFloat() * 0.2f;
        BlockPos pos = owner.getBlockPos();
        server.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.6f, 0.7f);
        server.playSound(null, pos, SoundEvents.SHULKER_SHOOT, SoundSource.BLOCKS, 1.0f, pitch);
    }

    /** Traînée de particules du canon vers l'impact : rend le tir instantané visible sans entité-projectile. */
    private static void spawnTracer(ServerLevel server, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        for (int i = 1; i <= TRACER_STEPS; i++) {
            Vec3 p = from.add(delta.scale((double) i / TRACER_STEPS));
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Origine des tirs : à la fois le départ du tracer de particules ET l'origine du test de ligne
     * de vue, donc pas seulement cosmétique — trop bas, la tourelle se croit bloquée par son propre
     * bloc ou par le sol tout autour. Alignée sur l'axe du canon du modèle via
     * {@link #MUZZLE_HEIGHT_PX} ; horizontalement on reste au centre du bloc (le canon pivote, il
     * n'y a pas de « bonne » position fixe, et le centre est le seul choix qui ne dérive pas selon
     * le lacet).
     */
    private Vec3 muzzlePos() {
        return Vec3.atBottomCenterOf(owner.getBlockPos()).add(0, MUZZLE_HEIGHT_PX / 16.0, 0);
    }

    private void setCurrentTarget(@Nullable LivingEntity target) {
        int id = target == null ? -1 : target.getId();
        if (id != currentTargetId) {
            currentTargetId = id;
            syncToClient.run();
        }
    }

    // ----- Persistance / sync (le propriétaire choisit le mécanisme : saveAdditional vanilla ou write/read Create) -----

    public void save(CompoundTag tag) {
        tag.putInt("range", range);
        tag.putBoolean("active", active);
        tag.putBoolean("targetHostile", targetHostile);
        tag.putBoolean("targetNeutral", targetNeutral);
        tag.putBoolean("targetPlayer", targetPlayer);
        tag.putLongArray("linked", linked.stream().mapToLong(BlockPos::asLong).toArray());
    }

    public void load(CompoundTag tag) {
        if (tag.contains("range")) range = clampRange(tag.getInt("range"));
        if (tag.contains("active")) active = tag.getBoolean("active");
        if (tag.contains("targetHostile")) targetHostile = tag.getBoolean("targetHostile");
        if (tag.contains("targetNeutral")) targetNeutral = tag.getBoolean("targetNeutral");
        if (tag.contains("targetPlayer")) targetPlayer = tag.getBoolean("targetPlayer");

        linked.clear();
        for (long packed : tag.getLongArray("linked")) {
            linked.add(BlockPos.of(packed));
        }
    }

    /** Transitoire (paquets réseau uniquement) : jamais persisté sur disque, cf. {@link #save}. */
    public void saveTransient(CompoundTag tag) {
        tag.putInt("currentTargetId", currentTargetId);
        tag.putBoolean("hasAmmo", hasAmmo);
    }

    public void loadTransient(CompoundTag tag) {
        if (tag.contains("currentTargetId")) currentTargetId = tag.getInt("currentTargetId");
        if (tag.contains("hasAmmo")) hasAmmo = tag.getBoolean("hasAmmo");
    }
}
