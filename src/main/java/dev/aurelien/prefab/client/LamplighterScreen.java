package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.LamplighterBlockEntity;
import dev.aurelien.prefab.menu.LamplighterMenu;
import dev.aurelien.prefab.network.LamplighterActionPayload;
import dev.aurelien.prefab.network.SetCenterPayload;
import dev.aurelien.prefab.network.SetLamplighterRangePayload;
import dev.aurelien.prefab.network.SetLamplighterSpacingPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Disposition sur DEUX colonnes, gabarit commun à {@link MachineScreen} : réglages à gauche, liste des
 * matériaux à droite (à la place de la colonne outil des autres machines, l'allumeur n'ayant pas de
 * slot), puis info / action / statut sur toute la largeur, sous le bas de la colonne droite. C'est ce
 * qui libère la rangée d'action pour le couple Démarrer + Centre, à la même place que sur la niveleuse
 * et le texturiseur.
 */
public class LamplighterScreen extends MachineScreen<LamplighterMenu> {
    private static final int Y_RANGE = Y_ROW0;
    private static final int Y_SPACING = Y_ROW0 + ROW_STEP;

    /** Colonne matériaux : icône du bloc + quantité, une rangée par matériau. */
    private static final int MATERIALS_X = RIGHT_X;
    private static final int MATERIAL_ROW_H = 16;

    private int range;
    private int spacing;

    private Button toggleButton;

    public LamplighterScreen(LamplighterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected int accentColor() {
        return 0xFFC45C; // cœur chaud de la lanterne
    }

    private LamplighterBlockEntity be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof LamplighterBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();

        LamplighterBlockEntity be = be();
        if (be != null) {
            range = be.range();
            spacing = be.spacing();
        } else {
            range = LamplighterBlockEntity.DEFAULT_RANGE;
            spacing = LamplighterBlockEntity.DEFAULT_SPACING;
        }

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            range = Mth.clamp(range - 1, LamplighterBlockEntity.MIN_RANGE, LamplighterBlockEntity.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + MINUS_X, topPos + Y_RANGE, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            range = Mth.clamp(range + 1, LamplighterBlockEntity.MIN_RANGE, LamplighterBlockEntity.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + PLUS_X, topPos + Y_RANGE, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.lamplighter.max"), b -> {
            range = LamplighterBlockEntity.MAX_RANGE;
            sendRange();
        }).bounds(leftPos + MAX_X, topPos + Y_RANGE, MAX_BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            spacing = Mth.clamp(spacing - 1, LamplighterBlockEntity.MIN_SPACING, LamplighterBlockEntity.MAX_SPACING);
            sendSpacing();
        }).bounds(leftPos + MINUS_X, topPos + Y_SPACING, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            spacing = Mth.clamp(spacing + 1, LamplighterBlockEntity.MIN_SPACING, LamplighterBlockEntity.MAX_SPACING);
            sendSpacing();
        }).bounds(leftPos + PLUS_X, topPos + Y_SPACING, SMALL_BTN_W, BTN_H).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            LamplighterBlockEntity current = be();
            boolean next = current == null || !current.active();
            PacketDistributor.sendToServer(new LamplighterActionPayload(menu.pos(), next));
        }).bounds(leftPos + LABEL_X, topPos + Y_ACTION_ROW, START_BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.machine.set_center"), b -> {
            PacketDistributor.sendToServer(new SetCenterPayload(menu.pos()));
        }).bounds(leftPos + CENTER_BTN_X, topPos + Y_ACTION_ROW, CENTER_BTN_W, BTN_H)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.machine.set_center.tooltip")))
                .build());
    }

    private void sendRange() {
        PacketDistributor.sendToServer(new SetLamplighterRangePayload(menu.pos(), range));
    }

    private void sendSpacing() {
        PacketDistributor.sendToServer(new SetLamplighterSpacingPayload(menu.pos(), spacing));
    }

    private Component toggleLabel() {
        LamplighterBlockEntity be = be();
        boolean active = be != null && be.active();
        return Component.translatable(active ? "gui.turnkey_factory.lamplighter.stop" : "gui.turnkey_factory.lamplighter.start");
    }

    private ItemStack hoveredIcon = ItemStack.EMPTY;
    private Component hoveredText = null;

    /** Icône réelle du bloc + quantité (stock/manquant), même format que la liste de matériaux du contrôleur. */
    private void materialRow(GuiGraphics g, int mouseX, int mouseY, int rowY, ItemStack icon, int required, int available, Component tooltipOverride) {
        int iconX = leftPos + MATERIALS_X;
        g.renderItem(icon, iconX, rowY);
        if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= rowY && mouseY < rowY + 16) {
            if (tooltipOverride != null) hoveredText = tooltipOverride;
            else hoveredIcon = icon;
        }
        int missing = Math.max(0, required - available);
        Component qty = missing > 0
                ? Component.literal("×" + required + " ").append(Component.translatable("gui.turnkey_factory.book.missing", missing))
                : Component.literal("×" + required);
        g.drawString(font, qty, iconX + 20, rowY + 4, missing > 0 ? COLOR_WARN : COLOR_GOOD, false);
    }

    private static Item resolveLogItem(String registryId) {
        if (registryId.isEmpty()) return Items.OAK_LOG;
        ResourceLocation loc = ResourceLocation.tryParse(registryId);
        if (loc == null) return Items.OAK_LOG;
        return BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.OAK_LOG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoveredIcon = ItemStack.EMPTY;
        hoveredText = null;
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;

        label(g, Y_RANGE, Component.translatable("gui.turnkey_factory.lamplighter.range"), range);
        label(g, Y_SPACING, Component.translatable("gui.turnkey_factory.lamplighter.spacing"), spacing);

        header(g, Component.translatable("gui.turnkey_factory.machine.materials"), leftPos + MATERIALS_X, topPos + Y_ROW0);

        int maxTextWidth = textWidth();
        LamplighterBlockEntity be = be();
        if (be != null) {
            int required = be.totalLamps();
            int rowY = topPos + Y_RIGHT_ROW0;
            materialRow(g, mouseX, mouseY, rowY, new ItemStack(Items.TORCH), required, be.availTorch(), null);
            materialRow(g, mouseX, mouseY, rowY + MATERIAL_ROW_H, new ItemStack(Items.IRON_INGOT), required, be.availIron(), null);

            boolean anySpecies = be.speciesLogId().isEmpty();
            Item logItem = resolveLogItem(be.speciesLogId());
            Component logTooltip = anySpecies ? Component.translatable("gui.turnkey_factory.lamplighter.any_log") : null;
            materialRow(g, mouseX, mouseY, rowY + 2 * MATERIAL_ROW_H, new ItemStack(logItem), required, be.availLog(), logTooltip);

            // Pleine largeur, sous la colonne matériaux : même bande que l'info des autres machines.
            drawWrapped(g, Component.translatable("gui.turnkey_factory.lamplighter.materials", required),
                    lx, topPos + Y_INFO, maxTextWidth, COLOR_HEADER);
        }

        int statusY = topPos + Y_STATUS;
        if (be != null) {
            statusY = drawChecklist(g, lx, topPos + Y_STATUS, maxTextWidth,
                    check("gui.turnkey_factory.lamplighter.checklist.link", be.hasLink()),
                    check("gui.turnkey_factory.lamplighter.checklist.species", be.hasSpecies()),
                    check("gui.turnkey_factory.lamplighter.checklist.material", be.hasMaterial()));
        }

        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
            statusColor = COLOR_IDLE;
        } else {
            switch (be.status()) {
                case LamplighterBlockEntity.STATUS_WORKING -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.working", be.queueSize());
                    statusColor = COLOR_WORKING;
                }
                case LamplighterBlockEntity.STATUS_MISSING_MATERIAL -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.missing_material");
                    statusColor = COLOR_WARN;
                }
                case LamplighterBlockEntity.STATUS_DONE -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.done");
                    statusColor = COLOR_GOOD;
                }
                case LamplighterBlockEntity.STATUS_NO_SPECIES -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.no_species");
                    statusColor = COLOR_ERROR;
                }
                case LamplighterBlockEntity.STATUS_NO_LINK -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.no_link");
                    statusColor = COLOR_ERROR;
                }
                default -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.inactive");
                    statusColor = COLOR_IDLE;
                }
            }
        }
        drawWrapped(g, status, lx, statusY, maxTextWidth, statusColor);

        if (toggleButton != null) {
            toggleButton.setMessage(toggleLabel());
        }

        if (hoveredText != null) {
            g.renderTooltip(font, hoveredText, mouseX, mouseY);
        } else if (!hoveredIcon.isEmpty()) {
            g.renderTooltip(font, hoveredIcon, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }
}
