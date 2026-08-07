package dev.aurelien.prefab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Ciblage et tir de la tourelle, extrait de {@link TurretBlockEntity} pour être partagé avec
 * l'implémentation Create (compat/create) — les deux BlockEntity ne peuvent pas partager de classe
 * mère (l'une étend {@code BlockEntity}, l'autre {@code KineticBlockEntity}), donc composition
 * plutôt qu'héritage. Le coût en énergie (charbon consommé, ou vitesse de rotation suffisante) est
 * entièrement délégué au propriétaire via {@code tryConsumePower} : {@link #tryFire} l'appelle une
 * seule fois, juste avant d'infliger les dégâts, et annule le tir (sans perdre la cible) s'il
 * renvoie {@code false}.
 */
public class TurretCombat {
    public static final int MIN_RANGE = 4;
    public static final int MAX_RANGE = 32;
    public static final int DEFAULT_RANGE = 12;

    private static final int SCAN_INTERVAL = 10;
    private static final int FIRE_INTERVAL = 20;
    private static final float DAMAGE = 4.0f;
    private static final int TRACER_STEPS = 12;

    private final BlockEntity owner;
    private final BooleanSupplier tryConsumePower;
    private final Runnable syncToClient;

    private int range = DEFAULT_RANGE;
    private boolean active = false;
    private boolean targetHostile = true;
    private boolean targetNeutral = false;
    private boolean targetPlayer = false;

    private int scanCooldown = 0;
    private int fireCooldown = 0;
    /** -1 = aucune cible. Seule donnée de visée envoyée au client (cf. javadoc de {@link TurretBlockEntity}). */
    private int currentTargetId = -1;

    public TurretCombat(BlockEntity owner, BooleanSupplier tryConsumePower, Runnable syncToClient) {
        this.owner = owner;
        this.tryConsumePower = tryConsumePower;
        this.syncToClient = syncToClient;
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
        if (!active) {
            return;
        }

        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            acquireTarget(server);
        }

        if (currentTargetId >= 0 && --fireCooldown <= 0) {
            fireCooldown = FIRE_INTERVAL;
            tryFire(server);
        }
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

        // En panne d'énergie (charbon épuisé ou rotation insuffisante) : la cible reste verrouillée,
        // nouvel essai au prochain FIRE_INTERVAL — pas de tir manqué définitif pour une panne temporaire.
        if (!tryConsumePower.getAsBoolean()) {
            return;
        }

        target.hurt(server.damageSources().magic(), DAMAGE);
        playFireSound(server);
        spawnTracer(server, origin, targetPos);
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

    private Vec3 muzzlePos() {
        return Vec3.atCenterOf(owner.getBlockPos()).add(0, 0.7, 0);
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
    }

    public void load(CompoundTag tag) {
        if (tag.contains("range")) range = clampRange(tag.getInt("range"));
        if (tag.contains("active")) active = tag.getBoolean("active");
        if (tag.contains("targetHostile")) targetHostile = tag.getBoolean("targetHostile");
        if (tag.contains("targetNeutral")) targetNeutral = tag.getBoolean("targetNeutral");
        if (tag.contains("targetPlayer")) targetPlayer = tag.getBoolean("targetPlayer");
    }

    /** Transitoire (paquets réseau uniquement) : jamais persisté sur disque, cf. {@link #save}. */
    public void saveTransient(CompoundTag tag) {
        tag.putInt("currentTargetId", currentTargetId);
    }

    public void loadTransient(CompoundTag tag) {
        if (tag.contains("currentTargetId")) currentTargetId = tag.getInt("currentTargetId");
    }
}
