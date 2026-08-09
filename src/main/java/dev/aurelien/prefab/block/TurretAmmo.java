package dev.aurelien.prefab.block;

import dev.aurelien.prefab.build.InventoryNetwork;
import dev.aurelien.prefab.reg.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Munitions <em>solides</em>, piochées dans les inventaires liés : c'est l'approvisionnement de la
 * mitrailleuse, et donc le comportement par défaut de {@link TurretWeaponBlock}. Extrait de
 * {@link TurretCombat} le jour où une arme a cessé de tirer des pépites (le lance-flammes puise
 * dans le réservoir du socle, cf. {@link TurretTank}) : le ciblage est commun à toutes les armes,
 * pas la munition.
 * <p>
 * Quatre paliers, du plus faible au plus fort :
 * <table border="1">
 *   <caption>Paliers de munitions</caption>
 *   <tr><th>munition</th><th>dégâts</th><th>effet</th></tr>
 *   <tr><td>pépite de cuivre</td><td>×0,5</td><td>—</td></tr>
 *   <tr><td>pépite de fer</td><td>×1,0</td><td>—</td></tr>
 *   <tr><td>obus perforant</td><td>×2,0</td><td>—</td></tr>
 *   <tr><td>obus incendiaire</td><td>×2,0</td><td>enflamme 5 s</td></tr>
 * </table>
 * Le multiplicateur porte sur les dégâts de base de l'<em>arme montée</em>
 * ({@link TurretWeaponBlock#baseDamage}), relue à chaque tir : changer d'arme change la munition de
 * valeur sans rien recalculer ici. Les pépites sont reconnues par tag conventionnel
 * ({@code c:nuggets/iron} / {@link #NUGGETS_COPPER}) et non par item précis, donc celle de n'importe
 * quel mod fait office de munition, pas seulement la nôtre.
 * <p>
 * <strong>Ordre de consommation :</strong> celui des emplacements, pas un tirage au sort
 * ({@link InventoryNetwork#extractFirstEligible}). Le coffre se vide donc de haut à gauche vers le
 * bas à droite, et ranger ses munitions suffit à décider dans quel ordre elles partent — un tirage
 * pondéré par les quantités rendait l'obus perforant invisible dès qu'il côtoyait une grosse réserve
 * de pépites. La garantie ne vaut qu'<em>à l'intérieur</em> d'un inventaire : entre coffres, l'ordre
 * est celui de la découverte du flood-fill, qui n'a rien de visuel.
 */
public final class TurretAmmo {
    private TurretAmmo() {}

    /**
     * Munitions désignées par tag conventionnel et non par item précis : n'importe quelle pépite de
     * cuivre est acceptée, quel que soit le mod qui la fournit (Create en ajoute une, comme plusieurs
     * autres mods), et la nôtre est déclarée dans le même tag (cf. {@code data/c/tags/item/nuggets/copper.json}).
     * {@code c:nuggets/copper} n'a pas de constante dans {@code Tags.Items} — le cuivre n'a pas de
     * pépite vanilla en 1.21.1 — d'où la clé construite à la main, avec l'identifiant conventionnel
     * que tout le monde utilise.
     * <p>
     * Seule la <em>clé</em> est statique, jamais l'appartenance : les tags sont vides tant que le
     * datapack n'est pas chargé, donc toute lecture doit rester au moment du tick.
     */
    private static final TagKey<Item> NUGGETS_COPPER =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "nuggets/copper"));

    /** Palier : multiplicateur appliqué aux dégâts de base de l'arme, et durée d'embrasement ({@code 0} = aucune). */
    private record Tier(float damageMultiplier, float igniteSeconds) {}

    private static final Tier TIER_COPPER = new Tier(0.5f, 0f);
    private static final Tier TIER_IRON = new Tier(1.0f, 0f);
    private static final Tier TIER_SLUG = new Tier(2.0f, 0f);
    /** Même impact que l'obus perforant : la poudre paie l'embrasement, pas des dégâts bruts. */
    private static final Tier TIER_INCENDIARY = new Tier(2.0f, 5f);

    /**
     * Palier correspondant à {@code item}, ou {@code null} si ce n'est pas une munition. Les obus
     * manufacturés sont reconnus par item précis (ce sont les nôtres), les pépites par tag
     * conventionnel, pour que celles de n'importe quel mod fassent l'affaire. Le fer est testé avant
     * le cuivre : un item exotique déclaré dans les deux tags garde alors les dégâts pleins, au lieu
     * que le résultat dépende de l'ordre des branches.
     * <p>
     * Les {@code .get()} restent <strong>dans</strong> la méthode : un champ statique initialisé
     * depuis un {@code DeferredItem} se résoudrait au chargement de la classe, avant que le registre
     * ne soit rempli.
     */
    @Nullable
    private static Tier tierOf(Item item) {
        if (item == ModItems.AMMO_INCENDIARY.get()) return TIER_INCENDIARY;
        if (item == ModItems.AMMO_SLUG.get()) return TIER_SLUG;
        ItemStack stack = new ItemStack(item);
        if (stack.is(Tags.Items.NUGGETS_IRON)) return TIER_IRON;
        if (stack.is(NUGGETS_COPPER)) return TIER_COPPER;
        return null;
    }

    private static boolean isAmmo(Item item) {
        return tierOf(item) != null;
    }

    /** Sondage <strong>non destructif</strong> : y a-t-il au moins une munition à portée de main ? */
    public static boolean hasAny(ServerLevel server, List<BlockPos> linked) {
        return InventoryNetwork.countEligible(server, linked, TurretAmmo::isAmmo) > 0;
    }

    /**
     * Prélève une munition et en dérive le profil du tir, ou {@code null} si l'extraction n'a rien
     * donné. Ne fait <em>aucun</em> sondage préalable : l'appelant est censé avoir déjà validé
     * {@link #hasAny} puis l'énergie, dans cet ordre (cf. {@code TurretCombat#tryFire}) — c'est cet
     * enchaînement qui évite d'avoir à remettre une pépite dans un coffre après une panne de courant.
     */
    @Nullable
    public static TurretWeaponBlock.Shot consume(ServerLevel server, List<BlockPos> linked, float baseDamage) {
        Item ammo = InventoryNetwork.extractFirstEligible(server, linked, TurretAmmo::isAmmo);
        if (ammo == null) return null;
        Tier tier = tierOf(ammo);
        // Inatteignable : isAmmo, donc tierOf, vient de filtrer cet item.
        if (tier == null) return null;
        return new TurretWeaponBlock.Shot(baseDamage * tier.damageMultiplier(), tier.igniteSeconds(), 0, 0);
    }
}
