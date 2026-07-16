package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.LevelerBlockEntity;
import dev.aurelien.prefab.menu.LevelerMenu;
import dev.aurelien.prefab.network.LevelerActionPayload;
import dev.aurelien.prefab.network.SetLevelerDimsPayload;
import dev.aurelien.prefab.network.SetLevelerTargetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class LevelerScreen extends AbstractContainerScreen<LevelerMenu> {
    private static final int COL2_DX = 160;

    private static final int Y_HEADER = 6;
    private static final int Y_ROW1 = 20, Y_ROW2 = 42, Y_ROW3 = 64;
    private static final int Y_FILL_INFO = 90;
    private static final int Y_SHOVEL_LABEL = 100;
    private static final int Y_ACTION_ROW = 108;
    private static final int Y_STATUS = 134;

    private static final int LABEL_X = 12;
    private static final int MINUS_X = 72;
    private static final int VALUE_X = 98;
    private static final int PLUS_X = 120;

    // Ligne Démarrer/Pelle côte à côte : moitié gauche = bouton, moitié droite = slot.
    private static final int HALF_GAP = 8;
    private static final int LEFT_X0 = LABEL_X;

    private int w, l;
    private int ox, oz, targetY, fillDepth;

    private final List<Button> configButtons = new ArrayList<>();
    private Button toggleButton;

    public LevelerScreen(LevelerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 310;
        this.imageHeight = 238;
    }

    private LevelerBlockEntity be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof LevelerBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();
        configButtons.clear();

        LevelerBlockEntity be = be();
        if (be != null) {
            w = be.width(); l = be.length();
            ox = be.offsetX(); oz = be.offsetZ(); targetY = be.targetOffsetY(); fillDepth = be.fillDepth();
        } else {
            w = l = 7; ox = oz = targetY = 0; fillDepth = LevelerBlockEntity.DEFAULT_FILL_DEPTH;
        }

        row(MINUS_X, PLUS_X, Y_ROW1, () -> w, v -> w = v, LevelerBlockEntity.MIN_SIZE, LevelerBlockEntity.MAX_SIZE,
                LevelerBlockEntity.SIZE_STEP, this::sendDims);
        row(MINUS_X, PLUS_X, Y_ROW2, () -> l, v -> l = v, LevelerBlockEntity.MIN_SIZE, LevelerBlockEntity.MAX_SIZE,
                LevelerBlockEntity.SIZE_STEP, this::sendDims);
        row(MINUS_X + COL2_DX, PLUS_X + COL2_DX, Y_ROW1, () -> ox, v -> ox = v, -LevelerBlockEntity.OFFSET_MAX, LevelerBlockEntity.OFFSET_MAX, 1, this::sendTarget);
        row(MINUS_X + COL2_DX, PLUS_X + COL2_DX, Y_ROW2, () -> oz, v -> oz = v, -LevelerBlockEntity.OFFSET_MAX, LevelerBlockEntity.OFFSET_MAX, 1, this::sendTarget);
        row(MINUS_X, PLUS_X, Y_ROW3, () -> targetY, v -> targetY = v, -LevelerBlockEntity.TARGET_MAX, LevelerBlockEntity.TARGET_MAX, 1, this::sendTarget);
        row(MINUS_X + COL2_DX, PLUS_X + COL2_DX, Y_ROW3, () -> fillDepth, v -> fillDepth = v, LevelerBlockEntity.MIN_FILL_DEPTH,
                LevelerBlockEntity.MAX_FILL_DEPTH, 1, this::sendTarget);

        int leftHalfWidth = imageWidth / 2 - HALF_GAP - LEFT_X0;
        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            LevelerBlockEntity current = be();
            boolean next = current == null || !current.active();
            PacketDistributor.sendToServer(new LevelerActionPayload(menu.pos(), next));
        }).bounds(leftPos + LEFT_X0, topPos + Y_ACTION_ROW, leftHalfWidth, 20).build());
    }

    private void row(int minusX, int plusX, int y, IntSupplier get, IntConsumer set, int min, int max, int step, Runnable onChange) {
        configButtons.add(addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            set.accept(Mth.clamp(get.getAsInt() - step, min, max));
            onChange.run();
        }).bounds(leftPos + minusX, topPos + y, 20, 20).build()));
        configButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            set.accept(Mth.clamp(get.getAsInt() + step, min, max));
            onChange.run();
        }).bounds(leftPos + plusX, topPos + y, 20, 20).build()));
    }

    private void sendDims() {
        PacketDistributor.sendToServer(new SetLevelerDimsPayload(menu.pos(), w, l));
    }

    private void sendTarget() {
        PacketDistributor.sendToServer(new SetLevelerTargetPayload(menu.pos(), ox, oz, targetY, fillDepth));
    }

    private Component toggleLabel() {
        LevelerBlockEntity be = be();
        boolean active = be != null && be.active();
        return Component.translatable(active ? "gui.turnkey_factory.leveler.stop" : "gui.turnkey_factory.leveler.start");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xD0101010);
        // Case visible pour chaque emplacement (pelle/pioche + inventaire joueur) : sans ça, un slot vide
        // est invisible et on ne sait pas où poser un objet, ni distinguer les slots machine de l'inventaire.
        for (Slot slot : menu.slots) {
            slotBg(g, leftPos + slot.x - 1, topPos + slot.y - 1, slot.index < 2);
        }
    }

    /** {@code highlight} distingue les slots pelle/pioche (bordure violette) des slots d'inventaire (gris). */
    private void slotBg(GuiGraphics g, int x, int y, boolean highlight) {
        g.fill(x, y, x + 18, y + 18, highlight ? 0xFFB080FF : 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;
        int vx = leftPos + VALUE_X;
        int lx2 = lx + COL2_DX;
        int vx2 = vx + COL2_DX;

        g.drawString(font, Component.translatable("gui.turnkey_factory.leveler.zone"), lx, topPos + Y_HEADER, 0xC0C0FF, false);
        label(g, lx, vx, Y_ROW1, Component.translatable("gui.turnkey_factory.width"), w);
        label(g, lx, vx, Y_ROW2, Component.translatable("gui.turnkey_factory.length"), l);
        label(g, lx, vx, Y_ROW3, Component.translatable("gui.turnkey_factory.leveler.target_height"), targetY);

        g.drawString(font, Component.translatable("gui.turnkey_factory.leveler.offset"), lx2, topPos + Y_HEADER, 0xC0C0FF, false);
        label(g, lx2, vx2, Y_ROW1, Component.translatable("gui.turnkey_factory.axis_x"), ox);
        label(g, lx2, vx2, Y_ROW2, Component.translatable("gui.turnkey_factory.axis_z"), oz);
        label(g, lx2, vx2, Y_ROW3, Component.translatable("gui.turnkey_factory.leveler.fill_depth"), fillDepth);

        int rightHalfCenter = leftPos + imageWidth / 2 + HALF_GAP + (imageWidth / 2 - HALF_GAP - LABEL_X) / 2;
        Component toolsLabel = Component.translatable("gui.turnkey_factory.leveler.tools");
        g.drawString(font, toolsLabel, rightHalfCenter - font.width(toolsLabel) / 2, topPos + Y_SHOVEL_LABEL, 0xC0C0FF, false);

        LevelerBlockEntity be = be();

        if (be != null && be.fillNeeded() > 0) {
            int missing = Math.max(0, be.fillNeeded() - be.fillSupplied());
            Component fillInfo = missing > 0
                    ? Component.translatable("gui.turnkey_factory.leveler.fill_info.missing", be.fillNeeded(), be.fillSupplied(), missing)
                    : Component.translatable("gui.turnkey_factory.leveler.fill_info.covered", be.fillNeeded());
            g.drawString(font, fillInfo, lx, topPos + Y_FILL_INFO, missing > 0 ? 0xFFC040 : 0x80FF80, false);
        }
        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
            statusColor = 0xB0B0B0;
        } else {
            switch (be.status()) {
                case LevelerBlockEntity.STATUS_WORKING -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.working", be.queueSize());
                    statusColor = 0x80C0FF;
                }
                case LevelerBlockEntity.STATUS_MISSING_FILL -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.missing_fill");
                    statusColor = 0xFFC040;
                }
                case LevelerBlockEntity.STATUS_DONE -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.done");
                    statusColor = 0x80FF80;
                }
                case LevelerBlockEntity.STATUS_NO_SHOVEL -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_shovel");
                    statusColor = 0xFF6060;
                }
                case LevelerBlockEntity.STATUS_NO_PICKAXE -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_pickaxe");
                    statusColor = 0xFF6060;
                }
                case LevelerBlockEntity.STATUS_NO_LINK -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_link");
                    statusColor = 0xFF6060;
                }
                default -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.inactive");
                    statusColor = 0xB0B0B0;
                }
            }
        }
        g.drawString(font, status, lx, topPos + Y_STATUS, statusColor, false);

        if (toggleButton != null) {
            toggleButton.setMessage(toggleLabel());
        }

        renderTooltip(g, mouseX, mouseY);
    }

    private void label(GuiGraphics g, int labelX, int valueX, int rowY, Component name, int v) {
        int textY = topPos + rowY + 6;
        g.drawString(font, name, labelX, textY, 0xFFFFFF, false);
        g.drawString(font, String.valueOf(v), valueX, textY, 0xFFE070, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // fenêtre épurée : pas de titre vanilla ni libellé d'inventaire
    }
}
