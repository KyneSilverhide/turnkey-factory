package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.ITurret;
import dev.aurelien.prefab.block.TurretCombat;
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

public class TurretScreen extends MachineScreen<TurretMenu> {
    private static final int Y_HEADER = 6;
    private static final int Y_RANGE = 20;
    private static final int Y_TARGETS_LABEL = 46;
    private static final int Y_TARGETS = 56;
    private static final int Y_CHECKLIST = 84;
    private static final int Y_STATUS = 108;
    private static final int Y_FUEL = 130;
    private static final int FUEL_BAR_H = 10;

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
        this.imageWidth = 210;
        this.imageHeight = 230;
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
        }).bounds(leftPos + MINUS_X, topPos + Y_RANGE, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            range = Mth.clamp(range + 1, TurretCombat.MIN_RANGE, TurretCombat.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + PLUS_X, topPos + Y_RANGE, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.turret.max"), b -> {
            range = TurretCombat.MAX_RANGE;
            sendRange();
        }).bounds(leftPos + MAX_X, topPos + Y_RANGE, 32, 20).build());

        hostileButton = addRenderableWidget(Button.builder(targetLabel("hostile", hostile), b -> {
            hostile = !hostile;
            sendTargets();
            hostileButton.setMessage(targetLabel("hostile", hostile));
        }).bounds(leftPos + LABEL_X, topPos + Y_TARGETS, TARGET_BTN_W, 20).build());

        neutralButton = addRenderableWidget(Button.builder(targetLabel("neutral", neutral), b -> {
            neutral = !neutral;
            sendTargets();
            neutralButton.setMessage(targetLabel("neutral", neutral));
        }).bounds(leftPos + LABEL_X + TARGET_BTN_W + TARGET_BTN_GAP, topPos + Y_TARGETS, TARGET_BTN_W, 20).build());

        playerButton = addRenderableWidget(Button.builder(targetLabel("player", player), b -> {
            player = !player;
            sendTargets();
            playerButton.setMessage(targetLabel("player", player));
        }).bounds(leftPos + LABEL_X + 2 * (TARGET_BTN_W + TARGET_BTN_GAP), topPos + Y_TARGETS, TARGET_BTN_W, 20).build());
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
            statusColor = 0xB0B0B0;
        } else if (!be.hasWeapon()) {
            status = Component.translatable("gui.turnkey_factory.turret.status.no_weapon");
            statusColor = 0xB0B0B0;
        } else if (!be.active()) {
            status = Component.translatable("gui.turnkey_factory.turret.status.inactive");
            statusColor = 0xB0B0B0;
        } else if (be.currentTargetId() < 0) {
            status = Component.translatable("gui.turnkey_factory.turret.status.scanning");
            statusColor = 0x80C0FF;
        } else {
            Component name = targetName(be);
            status = name != null
                    ? Component.translatable("gui.turnkey_factory.turret.status.engaging", name)
                    : Component.translatable("gui.turnkey_factory.turret.status.scanning");
            statusColor = 0xFF8060;
        }
        drawWrapped(g, status, lx, topPos + Y_STATUS, maxTextWidth, statusColor);

        if (be != null) {
            // Les quatre conditions requises pour tirer, affichées côte à côte plutôt qu'un statut
            // unique ambigu — un joueur voit d'un coup d'œil laquelle bloque le tir au lieu de deviner.
            // « Arme » vient en premier : c'est la seule qui se règle en posant un bloc, pas ici.
            drawChecklist(g, lx, topPos + Y_CHECKLIST, maxTextWidth,
                    check("gui.turnkey_factory.turret.checklist.weapon", be.hasWeapon()),
                    check("gui.turnkey_factory.turret.checklist.redstone", be.active()),
                    check("gui.turnkey_factory.turret.checklist.power", be.hasPower()),
                    check("gui.turnkey_factory.turret.checklist.ammo", be.hasAmmo()));
            drawPowerGauge(g, be, lx, topPos + Y_FUEL, maxTextWidth);
        }

        if (hostileButton != null) hostileButton.setMessage(targetLabel("hostile", hostile));
        if (neutralButton != null) neutralButton.setMessage(targetLabel("neutral", neutral));
        if (playerButton != null) playerButton.setMessage(targetLabel("player", player));

        renderTooltip(g, mouseX, mouseY);
    }

    /** Jauge d'énergie (charge de charbon ou vitesse de rotation, cf. ITurret#powerFraction) — fond
     *  sombre, remplissage proportionnel, libellé centré par-dessus. */
    private void drawPowerGauge(GuiGraphics g, ITurret be, int x, int y, int width) {
        g.fill(x, y, x + width, y + FUEL_BAR_H, 0xFF202020);

        int fillW = Math.round(width * Mth.clamp(be.powerFraction(), 0f, 1f));
        if (fillW > 0) {
            int fillColor = be.hasPower() ? 0xFF4FA83D : 0xFF8B2E2E;
            g.fill(x, y, x + fillW, y + FUEL_BAR_H, fillColor);
        }

        g.drawCenteredString(font, be.powerLabel(), x + width / 2, y + 1, 0xFFFFFF);
    }
}
