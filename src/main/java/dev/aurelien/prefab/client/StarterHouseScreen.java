package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.StarterHouseBlockEntity;
import dev.aurelien.prefab.menu.StarterHouseMenu;
import dev.aurelien.prefab.network.StarterHouseBuildPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Le plus dépouillé des écrans du mod : rien à régler, un seul bouton. Il reprend malgré tout le
 * gabarit de {@link MachineScreen} — même panneau, même rythme vertical, bouton d'action à la place
 * exacte du « Démarrer » des autres machines — pour que le joueur retrouve ses repères.
 * <p>
 * L'avertissement (« le bloc sera détruit ») occupe la bande de statut, en teinte d'alerte : c'est
 * la seule conséquence irréversible du bouton, et elle doit être lue avant de cliquer, pas après.
 */
public class StarterHouseScreen extends MachineScreen<StarterHouseMenu> {
    /** Description du contenu, sous le titre de colonne et bien au-dessus de la bande d'info. */
    private static final int Y_CONTENTS = Y_ROW0 + 16;

    public StarterHouseScreen(StarterHouseMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected int accentColor() {
        return 0xD9A066; // sapin chaud, l'essence dominante de la maison
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.starter_house.build"), b -> {
            PacketDistributor.sendToServer(new StarterHouseBuildPayload(menu.pos()));
        }).bounds(leftPos + LABEL_X, topPos + Y_ACTION_ROW, START_BTN_W, BTN_H).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;
        int width = textWidth();

        header(g, Component.translatable("gui.turnkey_factory.starter_house.contents"), lx, topPos + Y_ROW0);
        drawWrapped(g, Component.translatable("gui.turnkey_factory.starter_house.contents.detail"),
                lx, topPos + Y_CONTENTS, width, COLOR_LABEL);

        drawWrapped(g, Component.translatable("gui.turnkey_factory.starter_house.size",
                        StarterHouseBlockEntity.SIZE_X, StarterHouseBlockEntity.SIZE_Y, StarterHouseBlockEntity.SIZE_Z),
                lx, topPos + Y_INFO, width, COLOR_HEADER);

        drawWrapped(g, Component.translatable("gui.turnkey_factory.starter_house.warning"),
                lx, topPos + Y_STATUS, width, COLOR_WARN);

        renderTooltip(g, mouseX, mouseY);
    }
}
