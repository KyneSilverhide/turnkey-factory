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
 */
public class TurretCreateBlockEntity extends KineticBlockEntity implements MenuProvider, ITurret {
    private final TurretCombat combat = new TurretCombat(this, this::tryConsumeRotation, this::notifyUpdate);

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
        if (level instanceof ServerLevel server) {
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
    @Override public void setRange(int r) { combat.setRange(r); }
    @Override public void setTargets(boolean hostile, boolean neutral, boolean player) { combat.setTargets(hostile, neutral, player); }

    @Override
    public boolean hasPower() {
        return isSpeedRequirementFulfilled();
    }

    @Override
    public Component powerLabel() {
        return Component.translatable("gui.turnkey_factory.turret.rotation", Math.round(Math.abs(getSpeed())));
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
