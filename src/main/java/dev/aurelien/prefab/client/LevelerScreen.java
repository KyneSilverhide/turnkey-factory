package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.LevelerBlockEntity;
import dev.aurelien.prefab.menu.LevelerMenu;
import dev.aurelien.prefab.network.LevelerActionPayload;
import dev.aurelien.prefab.network.SetCenterPayload;
import dev.aurelien.prefab.network.SetLevelerRangePayload;
import dev.aurelien.prefab.network.SetLevelerTargetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Disposition sur DEUX colonnes, gabarit commun à {@link MachineScreen} : réglages à gauche, colonne
 * outil à droite, puis info / action / statut sur toute la largeur, sous le bas de la colonne droite.
 */
public class LevelerScreen extends MachineScreen<LevelerMenu> {
    private static final int Y_RANGE = Y_ROW0;
    private static final int Y_TARGET = Y_ROW0 + ROW_STEP;
    private static final int Y_FILL_DEPTH = Y_ROW0 + 2 * ROW_STEP;

    private int range, targetY, fillDepth;

    private Button toggleButton;

    public LevelerScreen(LevelerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected int accentColor() {
        return 0x42CD68; // bulle du niveau à bulle
    }

    private LevelerBlockEntity be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof LevelerBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected boolean isAccentSlot(Slot slot) {
        return slot.index < 2; // pelle + pioche
    }

    @Override
    protected void init() {
        super.init();

        LevelerBlockEntity be = be();
        if (be != null) {
            range = be.range();
            targetY = be.targetOffsetY();
            fillDepth = be.fillDepth();
        } else {
            range = LevelerBlockEntity.DEFAULT_RANGE;
            targetY = 0;
            fillDepth = LevelerBlockEntity.DEFAULT_FILL_DEPTH;
        }

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            range = Mth.clamp(range - 1, LevelerBlockEntity.minRange(), LevelerBlockEntity.maxRange());
            sendRange();
        }).bounds(leftPos + MINUS_X, topPos + Y_RANGE, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            range = Mth.clamp(range + 1, LevelerBlockEntity.minRange(), LevelerBlockEntity.maxRange());
            sendRange();
        }).bounds(leftPos + PLUS_X, topPos + Y_RANGE, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.leveler.max"), b -> {
            range = LevelerBlockEntity.maxRange();
            sendRange();
        }).bounds(leftPos + MAX_X, topPos + Y_RANGE, MAX_BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            targetY = Mth.clamp(targetY - 1, -LevelerBlockEntity.TARGET_MAX, LevelerBlockEntity.TARGET_MAX);
            sendTarget();
        }).bounds(leftPos + MINUS_X, topPos + Y_TARGET, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            targetY = Mth.clamp(targetY + 1, -LevelerBlockEntity.TARGET_MAX, LevelerBlockEntity.TARGET_MAX);
            sendTarget();
        }).bounds(leftPos + PLUS_X, topPos + Y_TARGET, SMALL_BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            fillDepth = Mth.clamp(fillDepth - 1, LevelerBlockEntity.MIN_FILL_DEPTH, LevelerBlockEntity.MAX_FILL_DEPTH);
            sendTarget();
        }).bounds(leftPos + MINUS_X, topPos + Y_FILL_DEPTH, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            fillDepth = Mth.clamp(fillDepth + 1, LevelerBlockEntity.MIN_FILL_DEPTH, LevelerBlockEntity.MAX_FILL_DEPTH);
            sendTarget();
        }).bounds(leftPos + PLUS_X, topPos + Y_FILL_DEPTH, SMALL_BTN_W, BTN_H).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            LevelerBlockEntity current = be();
            boolean next = current == null || !current.active();
            PacketDistributor.sendToServer(new LevelerActionPayload(menu.pos(), next));
        }).bounds(leftPos + LABEL_X, topPos + Y_ACTION_ROW, START_BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.machine.set_center"), b -> {
            PacketDistributor.sendToServer(new SetCenterPayload(menu.pos()));
        }).bounds(leftPos + CENTER_BTN_X, topPos + Y_ACTION_ROW, CENTER_BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.machine.set_center.tooltip")))
                .build());
    }

    private void sendRange() {
        PacketDistributor.sendToServer(new SetLevelerRangePayload(menu.pos(), range));
    }

    private void sendTarget() {
        PacketDistributor.sendToServer(new SetLevelerTargetPayload(menu.pos(), targetY, fillDepth));
    }

    private Component toggleLabel() {
        LevelerBlockEntity be = be();
        boolean active = be != null && be.active();
        return Component.translatable(active ? "gui.turnkey_factory.leveler.stop" : "gui.turnkey_factory.leveler.start");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;

        label(g, Y_RANGE, Component.translatable("gui.turnkey_factory.leveler.range"), range);
        label(g, Y_TARGET, Component.translatable("gui.turnkey_factory.leveler.target_height"), targetY);
        label(g, Y_FILL_DEPTH, Component.translatable("gui.turnkey_factory.leveler.fill_depth"), fillDepth);

        rightHeader(g, Component.translatable("gui.turnkey_factory.leveler.tools"), Y_ROW0);

        int maxTextWidth = textWidth();
        LevelerBlockEntity be = be();

        // Pleine largeur : les slots pelle/pioche s'arrêtent bien au-dessus (cf. Y_RIGHT_ROW0 + 18).
        if (be != null && be.fillNeeded() > 0) {
            int missing = Math.max(0, be.fillNeeded() - be.fillSupplied());
            Component fillInfo = missing > 0
                    ? Component.translatable("gui.turnkey_factory.leveler.fill_info.missing", be.fillNeeded(), be.fillSupplied(), missing)
                    : Component.translatable("gui.turnkey_factory.leveler.fill_info.covered", be.fillNeeded());
            drawWrapped(g, fillInfo, lx, topPos + Y_INFO, maxTextWidth, missing > 0 ? COLOR_WARN : COLOR_GOOD);
        }

        int statusY = topPos + Y_STATUS;
        if (be != null) {
            statusY = drawChecklist(g, lx, topPos + Y_STATUS, maxTextWidth,
                    check("gui.turnkey_factory.leveler.checklist.link", be.hasLink()),
                    check("gui.turnkey_factory.leveler.checklist.shovel", be.hasShovel()),
                    check("gui.turnkey_factory.leveler.checklist.pickaxe", be.hasPickaxe()),
                    check("gui.turnkey_factory.leveler.checklist.fill", be.hasFill()));
        }

        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
            statusColor = COLOR_IDLE;
        } else {
            switch (be.status()) {
                case LevelerBlockEntity.STATUS_WORKING -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.working", be.queueSize());
                    statusColor = COLOR_WORKING;
                }
                case LevelerBlockEntity.STATUS_MISSING_FILL -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.missing_fill");
                    statusColor = COLOR_WARN;
                }
                case LevelerBlockEntity.STATUS_DONE -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.done");
                    statusColor = COLOR_GOOD;
                }
                case LevelerBlockEntity.STATUS_NO_SHOVEL -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_shovel");
                    statusColor = COLOR_ERROR;
                }
                case LevelerBlockEntity.STATUS_NO_PICKAXE -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_pickaxe");
                    statusColor = COLOR_ERROR;
                }
                case LevelerBlockEntity.STATUS_NO_LINK -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_link");
                    statusColor = COLOR_ERROR;
                }
                default -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.inactive");
                    statusColor = COLOR_IDLE;
                }
            }
        }
        drawWrapped(g, status, lx, statusY, maxTextWidth, statusColor);

        if (toggleButton != null) {
            toggleButton.setMessage(toggleLabel());
        }

        renderTooltip(g, mouseX, mouseY);
    }
}
