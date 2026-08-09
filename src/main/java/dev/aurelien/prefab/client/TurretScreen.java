package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.ITurret;
import dev.aurelien.prefab.block.TurretCombat;
import dev.aurelien.prefab.block.TurretTank;
import dev.aurelien.prefab.block.TurretWeaponBlock;
import dev.aurelien.prefab.menu.TurretMenu;
import dev.aurelien.prefab.network.SetTurretRangePayload;
import dev.aurelien.prefab.network.SetTurretTargetsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Dernier écran passé au gabarit partagé de {@link MachineScreen} (300×238) : il tenait avant dans
 * 210×230, où la jauge d'énergie finissait à 140 pour un inventaire qui démarrait à 144. Il n'y avait
 * littéralement pas la place d'une seconde jauge — celle du réservoir de lave du lance-flammes.
 * <p>
 * Une seule colonne, contrairement aux machines de terrain : la tourelle n'a ni slot outil ni liste
 * de matériaux, ses trois boutons de cible occupent toute la largeur utile, et le reste est du texte
 * pleine largeur. Le budget vertical reste serré (contenu de 0 à {@link #INV_Y}) : la dernière jauge
 * finit à 150 pour 152 disponibles, d'où des Y explicites plutôt qu'un empilement au fil de l'eau.
 */
public class TurretScreen extends MachineScreen<TurretMenu> {
    private static final int Y_HEADER = Y_ROW0;
    private static final int Y_RANGE = 22;
    private static final int Y_TARGETS_LABEL = 48;
    private static final int Y_TARGETS = 58;
    private static final int Y_CHECKLIST = 84;
    /**
     * Plancher du statut : la checklist tient sur une ligne à cette largeur, mais si une traduction
     * la fait passer à deux, {@code drawChecklist} rend un Y plus bas et c'est lui qui gagne — le
     * statut descend au lieu d'être écrit par-dessus.
     */
    private static final int Y_STATUS = 104;
    private static final int Y_POWER = 128;
    /** Jauge du réservoir, sous celle d'énergie et affichée seulement si l'arme montée en consomme. */
    private static final int Y_TANK = 140;
    private static final int GAUGE_H = 10;

    private static final int TARGET_BTN_W = 58;
    private static final int TARGET_BTN_GAP = 4;

    private int range;
    private boolean hostile;
    private boolean neutral;
    private boolean player;

    private Button hostileButton;
    private Button neutralButton;
    private Button playerButton;

    public TurretScreen(TurretMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected int accentColor() {
        return 0xFF463C; // cœur incandescent de l'optique
    }

    private ITurret be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof ITurret turret) {
            return turret;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();

        ITurret be = be();
        if (be != null) {
            range = be.range();
            hostile = be.targetHostile();
            neutral = be.targetNeutral();
            player = be.targetPlayer();
        } else {
            range = TurretCombat.DEFAULT_RANGE;
            hostile = true;
            neutral = false;
            player = false;
        }

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            range = Mth.clamp(range - 1, TurretCombat.MIN_RANGE, TurretCombat.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + MINUS_X, topPos + Y_RANGE, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            range = Mth.clamp(range + 1, TurretCombat.MIN_RANGE, TurretCombat.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + PLUS_X, topPos + Y_RANGE, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.turret.max"), b -> {
            range = TurretCombat.MAX_RANGE;
            sendRange();
        }).bounds(leftPos + MAX_X, topPos + Y_RANGE, MAX_BTN_W, BTN_H).build());

        hostileButton = addRenderableWidget(Button.builder(targetLabel("hostile", hostile), b -> {
            hostile = !hostile;
            sendTargets();
            hostileButton.setMessage(targetLabel("hostile", hostile));
        }).bounds(leftPos + LABEL_X, topPos + Y_TARGETS, TARGET_BTN_W, BTN_H).build());

        neutralButton = addRenderableWidget(Button.builder(targetLabel("neutral", neutral), b -> {
            neutral = !neutral;
            sendTargets();
            neutralButton.setMessage(targetLabel("neutral", neutral));
        }).bounds(leftPos + LABEL_X + TARGET_BTN_W + TARGET_BTN_GAP, topPos + Y_TARGETS, TARGET_BTN_W, BTN_H).build());

        playerButton = addRenderableWidget(Button.builder(targetLabel("player", player), b -> {
            player = !player;
            sendTargets();
            playerButton.setMessage(targetLabel("player", player));
        }).bounds(leftPos + LABEL_X + 2 * (TARGET_BTN_W + TARGET_BTN_GAP), topPos + Y_TARGETS, TARGET_BTN_W, BTN_H).build());
    }

    private void sendRange() {
        PacketDistributor.sendToServer(new SetTurretRangePayload(menu.pos(), range));
    }

    private void sendTargets() {
        PacketDistributor.sendToServer(new SetTurretTargetsPayload(menu.pos(), hostile, neutral, player));
    }

    private static Component targetLabel(String key, boolean on) {
        return Component.translatable("gui.turnkey_factory.turret." + key,
                Component.translatable(on ? "gui.turnkey_factory.turret.on" : "gui.turnkey_factory.turret.off"));
    }

    /** Nom de la cible verrouillée, résolu côté client depuis l'id synchronisé (jamais recalculé côté serveur). */
    private Component targetName(ITurret be) {
        if (minecraft == null || minecraft.level == null) return null;
        Entity e = minecraft.level.getEntity(be.currentTargetId());
        return e instanceof LivingEntity living ? living.getDisplayName() : null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;

        header(g, Component.translatable("gui.turnkey_factory.turret.zone"), lx, topPos + Y_HEADER);
        label(g, Y_RANGE, Component.translatable("gui.turnkey_factory.turret.range"), range);
        header(g, Component.translatable("gui.turnkey_factory.turret.targets"), lx, topPos + Y_TARGETS_LABEL);

        int maxTextWidth = textWidth();

        ITurret be = be();
        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
            statusColor = COLOR_IDLE;
        } else if (!be.hasWeapon()) {
            status = Component.translatable("gui.turnkey_factory.turret.status.no_weapon");
            statusColor = COLOR_IDLE;
        } else if (!be.active()) {
            status = Component.translatable("gui.turnkey_factory.turret.status.inactive");
            statusColor = COLOR_IDLE;
        } else if (be.currentTargetId() < 0) {
            status = Component.translatable("gui.turnkey_factory.turret.status.scanning");
            statusColor = COLOR_WORKING;
        } else {
            Component name = targetName(be);
            status = name != null
                    ? Component.translatable("gui.turnkey_factory.turret.status.engaging", name)
                    : Component.translatable("gui.turnkey_factory.turret.status.scanning");
            statusColor = 0xFF8060;
        }

        int statusY = topPos + Y_STATUS;
        if (be != null) {
            TurretWeaponBlock weapon = weapon(be);
            // Les quatre conditions requises pour tirer, affichées côte à côte plutôt qu'un statut
            // unique ambigu — un joueur voit d'un coup d'œil laquelle bloque le tir au lieu de deviner.
            // « Arme » vient en premier : c'est la seule qui se règle en posant un bloc, pas ici.
            // Le libellé de la dernière vient de l'arme montée : « Munitions » pour la mitrailleuse,
            // « Lave » pour le lance-flammes, qui ne consomme rien qu'on puisse ranger dans un coffre.
            Component ammoLabel = weapon != null
                    ? weapon.ammoStatusKey()
                    : Component.translatable("gui.turnkey_factory.turret.checklist.ammo");
            statusY = Math.max(statusY, drawChecklist(g, lx, topPos + Y_CHECKLIST, maxTextWidth,
                    check("gui.turnkey_factory.turret.checklist.weapon", be.hasWeapon()),
                    check("gui.turnkey_factory.turret.checklist.redstone", be.active()),
                    check("gui.turnkey_factory.turret.checklist.power", be.hasPower()),
                    new ChecklistItem(ammoLabel, be.hasAmmo())));

            gauge(g, lx, topPos + Y_POWER, maxTextWidth, be.powerFraction(), be.hasPower(), be.powerLabel());

            // Jauge du réservoir seulement sous une arme qui le consomme : sous une mitrailleuse elle
            // afficherait une réserve que rien n'utilise, et le socle en a toujours un (cf. TurretTank).
            int costPerShot = weapon == null ? 0 : weapon.tankCostPerShot();
            if (costPerShot > 0) {
                TurretTank tank = be.tank();
                // En tirs restants, comme la jauge de charbon juste au-dessus : deux unités
                // différentes côte à côte obligeraient le joueur à convertir de tête.
                Component label = Component.translatable("gui.turnkey_factory.turret.lava",
                        tank.amount() / costPerShot);
                gauge(g, lx, topPos + Y_TANK, maxTextWidth, tank.fraction(), tank.has(costPerShot), label);
            }
        }

        drawWrapped(g, status, lx, statusY, maxTextWidth, statusColor);

        if (hostileButton != null) hostileButton.setMessage(targetLabel("hostile", hostile));
        if (neutralButton != null) neutralButton.setMessage(targetLabel("neutral", neutral));
        if (playerButton != null) playerButton.setMessage(targetLabel("player", player));

        renderTooltip(g, mouseX, mouseY);
    }

    /**
     * Arme actuellement montée, lue dans le monde côté client (cf. {@link ITurret#weaponOn}) : son
     * {@code BlockState} est déjà répliqué, donc le libellé de munition et le coût par tir ne coûtent
     * pas un octet de réseau.
     */
    @Nullable
    private TurretWeaponBlock weapon(ITurret be) {
        return minecraft == null || minecraft.level == null
                ? null
                : ITurret.weaponOn(minecraft.level, be.getBlockPos());
    }

    /**
     * Une barre de jauge : fond sombre, remplissage proportionnel, libellé centré par-dessus. Sert
     * aux deux jauges (énergie et réservoir), qui doivent se ressembler au pixel près — elles sont
     * empilées, et deux dessins légèrement différents se liraient comme deux choses sans rapport.
     */
    private void gauge(GuiGraphics g, int x, int y, int width, float fraction, boolean ok, Component label) {
        g.fill(x, y, x + width, y + GAUGE_H, 0xFF202020);

        int fillW = Math.round(width * Mth.clamp(fraction, 0f, 1f));
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + GAUGE_H, ok ? 0xFF4FA83D : 0xFF8B2E2E);
        }

        g.drawCenteredString(font, label, x + width / 2, y + 1, 0xFFFFFF);
    }
}
