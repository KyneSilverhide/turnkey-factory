package dev.aurelien.prefab.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.aurelien.prefab.PrefabMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Socle commun aux écrans de machines : le fond texturé, le dessin des slots, et les primitives de
 * texte (libellé + valeur, texte replié, checklist) que les cinq écrans dupliquaient à l'identique.
 *
 * <p>Volontairement mince : pas de moteur de mise en page. Chaque écran garde ses propres constantes
 * de Y explicites — seules les colonnes X, le rythme vertical partagé (cf. {@link #Y_ROW0},
 * {@link #Y_ACTION_ROW}, {@link #Y_STATUS}) et les couleurs sont mutualisés, pour que le bouton
 * Démarrer tombe au même endroit d'un écran à l'autre.
 *
 * <h2>Budget vertical</h2>
 * Minecraft garantit une hauteur logique de 240 en échelle auto : les panneaux ne doivent pas
 * dépasser {@link #PANEL_H}, et le contenu doit tenir au-dessus de {@link #INV_Y}. La largeur est
 * bien moins contrainte (320 garanti), d'où le choix d'étaler horizontalement plutôt que d'empiler.
 */
public abstract class MachineScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private static final ResourceLocation PANEL =
            ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "panel/background");
    private static final ResourceLocation SLOT =
            ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "panel/slot");
    private static final ResourceLocation SLOT_TOOL =
            ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "panel/slot_tool");

    /** Gabarit partagé par les trois machines de terrain (niveleuse, texturiseur, allumeur). */
    protected static final int PANEL_W = 300;
    protected static final int PANEL_H = 238;
    /** Inventaire joueur centré dans le panneau : (PANEL_W - 9*18) / 2. */
    public static final int INV_X = (PANEL_W - 162) / 2;
    public static final int INV_Y = 154;
    public static final int HOTBAR_Y = 212;

    // Colonne gauche : libellé, moins, valeur, plus, Max.
    protected static final int LABEL_X = 12;
    protected static final int MINUS_X = 72;
    protected static final int VALUE_X = 98;
    protected static final int PLUS_X = 120;
    protected static final int MAX_X = 146;
    protected static final int SMALL_BTN_W = 20;
    protected static final int MAX_BTN_W = 32;

    /** Colonne droite (matériaux / slots outil) : commence après le bouton Max (146+32=178). */
    protected static final int RIGHT_X = 190;
    /** Centre du libellé de la colonne droite, et donc des slots outil qu'il coiffe. */
    public static final int RIGHT_CX = 252;
    /** Première rangée de la colonne droite, sous son libellé dessiné à {@link #Y_ROW0}. */
    public static final int Y_RIGHT_ROW0 = 22;

    // Rythme vertical partagé. Les rangées de paramètres démarrent à Y_ROW0 et avancent de ROW_STEP ;
    // en dessous, ces trois positions sont IDENTIQUES sur les trois machines pour que le bouton
    // Démarrer / Centre et le statut ne bougent pas d'un écran à l'autre.
    protected static final int Y_ROW0 = 8;
    protected static final int ROW_STEP = 22;
    protected static final int Y_INFO = 76;
    protected static final int Y_ACTION_ROW = 98;
    protected static final int Y_STATUS = 122;

    protected static final int BTN_H = 20;
    protected static final int START_BTN_W = 130;
    protected static final int CENTER_BTN_W = 70;
    protected static final int CENTER_BTN_GAP = 8;
    /** X du bouton Centre, toujours à droite du bouton Démarrer et à la même place partout. */
    protected static final int CENTER_BTN_X = LABEL_X + START_BTN_W + CENTER_BTN_GAP;

    protected static final int LINE_H = 10;
    protected static final int CHECKLIST_GAP = 6;

    /** Équerres d'angle : décalage depuis le bord du panneau (dans le canal sombre du cadre) et longueur d'un bras. */
    private static final int ACCENT_INSET = 5;
    private static final int ACCENT_ARM = 14;

    protected static final int COLOR_OK = 0x4FA83D;
    protected static final int COLOR_MISSING = 0xC24B4B;
    protected static final int COLOR_HEADER = 0xC0C0FF;
    protected static final int COLOR_LABEL = 0xFFFFFF;
    protected static final int COLOR_VALUE = 0xFFE070;
    protected static final int COLOR_IDLE = 0xB0B0B0;
    protected static final int COLOR_WORKING = 0x80C0FF;
    protected static final int COLOR_WARN = 0xFFC040;
    protected static final int COLOR_GOOD = 0x80FF80;
    protected static final int COLOR_ERROR = 0xFF6060;

    protected MachineScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    /** Largeur utile pour du texte pleine largeur, marges gauche/droite symétriques. */
    protected int textWidth() {
        return imageWidth - 2 * LABEL_X;
    }

    /** Slots à liseré violet (outil / machine) plutôt que gris (inventaire joueur). */
    protected boolean isAccentSlot(Slot slot) {
        return false;
    }

    /**
     * Couleur d'identité de la machine : équerres d'angle du cadre + titres de colonne. Chaque écran
     * reprend une teinte de la palette de la texture de son propre bloc (cf. {@code tools/gen_*_textures.py}),
     * pour que le panneau rappelle le bloc sur lequel le joueur vient de cliquer.
     */
    protected int accentColor() {
        return COLOR_HEADER;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Le panneau est translucide : blitSprite n'active pas le mélange lui-même.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blitSprite(PANEL, leftPos, topPos, imageWidth, imageHeight);
        accentBrackets(g);
        for (Slot slot : menu.slots) {
            g.blitSprite(isAccentSlot(slot) ? SLOT_TOOL : SLOT,
                    leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
    }

    /**
     * Équerres aux quatre angles, dans la teinte de la machine. Des équerres plutôt qu'un liseré sur
     * tout le périmètre : bien moins de pixels saturés à concurrencer le texte, et ça se lit comme un
     * détail voulu plutôt que comme une bordure.
     */
    private void accentBrackets(GuiGraphics g) {
        int c = 0xFF000000 | accentColor(); // opaque : un ARGB sans alpha ne dessinerait rien
        int x0 = leftPos + ACCENT_INSET;
        int y0 = topPos + ACCENT_INSET;
        int x1 = leftPos + imageWidth - ACCENT_INSET;   // exclusif
        int y1 = topPos + imageHeight - ACCENT_INSET;   // exclusif

        g.fill(x0, y0, x0 + ACCENT_ARM, y0 + 1, c);
        g.fill(x0, y0, x0 + 1, y0 + ACCENT_ARM, c);
        g.fill(x1 - ACCENT_ARM, y0, x1, y0 + 1, c);
        g.fill(x1 - 1, y0, x1, y0 + ACCENT_ARM, c);
        g.fill(x0, y1 - 1, x0 + ACCENT_ARM, y1, c);
        g.fill(x0, y1 - ACCENT_ARM, x0 + 1, y1, c);
        g.fill(x1 - ACCENT_ARM, y1 - 1, x1, y1, c);
        g.fill(x1 - 1, y1 - ACCENT_ARM, x1, y1, c);
    }

    /** Titre de colonne (gauche), dans la teinte d'identité de la machine. */
    protected void header(GuiGraphics g, Component text, int x, int y) {
        g.drawString(font, text, x, y, accentColor(), false);
    }

    /** Titre de la colonne droite, centré sur {@link #RIGHT_CX}. */
    protected void rightHeader(GuiGraphics g, Component text, int y) {
        g.drawString(font, text, leftPos + RIGHT_CX - font.width(text) / 2, topPos + y, accentColor(), false);
    }

    /** Libellé + valeur d'une rangée de réglage, alignés verticalement sur ses boutons -/+. */
    protected void label(GuiGraphics g, int rowY, Component name, int value) {
        int textY = topPos + rowY + 6;
        g.drawString(font, name, leftPos + LABEL_X, textY, COLOR_LABEL, false);
        g.drawString(font, String.valueOf(value), leftPos + VALUE_X, textY, COLOR_VALUE, false);
    }

    /** Découpe {@code text} sur plusieurs lignes plutôt que de le laisser déborder du panneau. */
    protected int drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, lineY, color, false);
            lineY += LINE_H;
        }
        return lineY;
    }

    protected record ChecklistItem(Component label, boolean ok) {}

    protected static ChecklistItem check(String key, boolean ok) {
        return new ChecklistItem(Component.translatable(key), ok);
    }

    /**
     * Enchaîne les items horizontalement (toutes les conditions visibles à la fois plutôt qu'un seul
     * statut « gagnant » : le joueur voit d'un coup d'œil laquelle bloque), passe à la ligne si la
     * largeur disponible est dépassée. Renvoie le Y juste sous la dernière ligne, pour enchaîner le
     * texte de statut en prose sans chevaucher la checklist.
     */
    protected int drawChecklist(GuiGraphics g, int x, int y, int maxWidth, ChecklistItem... items) {
        int cx = x, cy = y;
        for (ChecklistItem item : items) {
            int w = font.width(item.label());
            if (cx != x && cx + w > x + maxWidth) {
                cx = x;
                cy += LINE_H;
            }
            g.drawString(font, item.label(), cx, cy, item.ok() ? COLOR_OK : COLOR_MISSING, false);
            cx += w + CHECKLIST_GAP;
        }
        return cy + LINE_H;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // fenêtre épurée : pas de titre vanilla ni libellé d'inventaire
    }
}
