package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.ITurret;
import dev.aurelien.prefab.block.TurretCombat;
import dev.aurelien.prefab.menu.TurretMenu;
import dev.aurelien.prefab.network.SetTurretRangePayload;
import dev.aurelien.prefab.network.SetTurretTargetsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public class TurretScreen extends AbstractContainerScreen<TurretMenu> {
    private static final int Y_HEADER = 6;
    private static final int Y_RANGE = 20;
    private static final int Y_TARGETS_LABEL = 46;
    private static final int Y_TARGETS = 56;
    private static final int Y_CHECKLIST = 84;
    private static final int Y_STATUS = 108;
    private static final int Y_FUEL = 130;
    private static final int FUEL_BAR_H = 10;
    private static final int LINE_H = 10;
    private static final int CHECKLIST_GAP = 6;

    /** Mêmes teintes que le remplissage de la jauge d'énergie ({@link #drawPowerGauge}) — un seul
     *  langage de couleur "prêt/manquant" dans tout l'écran. */
    private static final int COLOR_OK = 0x4FA83D;
    private static final int COLOR_MISSING = 0xC24B4B;

    private static final int LABEL_X = 12;
    private static final int MINUS_X = 72;
    private static final int VALUE_X = 98;
    private static final int PLUS_X = 120;
    private static final int MAX_X = 146;
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

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xD0101010);
        for (Slot slot : menu.slots) {
            slotBg(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    private void slotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
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
        int vx = leftPos + VALUE_X;

        g.drawString(font, Component.translatable("gui.turnkey_factory.turret.zone"), lx, topPos + Y_HEADER, 0xC0C0FF, false);
        int rangeTextY = topPos + Y_RANGE + 6;
        g.drawString(font, Component.translatable("gui.turnkey_factory.turret.range"), lx, rangeTextY, 0xFFFFFF, false);
        g.drawString(font, String.valueOf(range), vx, rangeTextY, 0xFFE070, false);

        g.drawString(font, Component.translatable("gui.turnkey_factory.turret.targets"), lx, topPos + Y_TARGETS_LABEL, 0xC0C0FF, false);

        int maxTextWidth = imageWidth - LABEL_X - 8;

        ITurret be = be();
        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
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
            drawChecklist(g, be, lx, topPos + Y_CHECKLIST);
            drawPowerGauge(g, be, lx, topPos + Y_FUEL, maxTextWidth);
        }

        if (hostileButton != null) hostileButton.setMessage(targetLabel("hostile", hostile));
        if (neutralButton != null) neutralButton.setMessage(targetLabel("neutral", neutral));
        if (playerButton != null) playerButton.setMessage(targetLabel("player", player));

        renderTooltip(g, mouseX, mouseY);
    }

    /** Trois conditions requises pour tirer (cf. {@link ITurret#hasAmmo}/{@link ITurret#hasPower}/
     *  {@link ITurret#active}), affichées côte à côte plutôt qu'un statut unique ambigu — un joueur
     *  voit d'un coup d'œil laquelle bloque le tir au lieu de deviner. */
    private void drawChecklist(GuiGraphics g, ITurret be, int x, int y) {
        int cx = x;
        cx = drawChecklistItem(g, "gui.turnkey_factory.turret.checklist.redstone", be.active(), cx, y);
        cx = drawChecklistItem(g, "gui.turnkey_factory.turret.checklist.power", be.hasPower(), cx, y);
        drawChecklistItem(g, "gui.turnkey_factory.turret.checklist.ammo", be.hasAmmo(), cx, y);
    }

    private int drawChecklistItem(GuiGraphics g, String key, boolean ok, int x, int y) {
        Component label = Component.translatable(key);
        g.drawString(font, label, x, y, ok ? COLOR_OK : COLOR_MISSING, false);
        return x + font.width(label) + CHECKLIST_GAP;
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

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, lineY, color, false);
            lineY += LINE_H;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // fenêtre épurée : pas de titre vanilla ni libellé d'inventaire
    }
}
