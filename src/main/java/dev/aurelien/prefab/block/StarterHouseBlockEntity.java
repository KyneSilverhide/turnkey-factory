package dev.aurelien.prefab.block;

import com.mojang.logging.LogUtils;
import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.menu.StarterHouseMenu;
import dev.aurelien.prefab.reg.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Pose la maison de départ depuis le schéma {@code data/turnkey_factory/structure/starter_house.nbt}.
 *
 * <h2>Volontairement sans état</h2>
 * Pas de ticker, pas de {@code saveAdditional}, pas de paquet de synchronisation : tout ce que
 * le client a besoin de savoir (l'emprise du fantôme) se déduit de la position et de
 * l'orientation, déjà répliquées par le {@link BlockState}. Ce BlockEntity n'existe que pour
 * deux raisons — être le {@link MenuProvider} du bloc, et se laisser trouver par
 * {@code GhostRenderer} qui balaie les BlockEntities des chunks proches.
 *
 * <h2>Ancrage et rotation</h2>
 * Le schéma fait {@value #SIZE_X}×{@value #SIZE_Y}×{@value #SIZE_Z}, avec sa couche de fondation
 * (terre battue) en {@code y=0} et le sol habitable en {@code y=1}. Le bloc-machine occupe donc
 * la case locale {@link #ANCHOR} = (4,1,4) : le centre du sol de la maison, au niveau où il est
 * lui-même posé.
 * <p>
 * La rotation se fait autour de {@link #PIVOT} = (4,0,4), le centre de l'emprise au sol, et
 * <strong>pas</strong> autour du coin d'origine (ce que ferait {@code setRotation} seul) : sans
 * ce pivot, la maison partirait jusqu'à 8 blocs de côté sur trois des quatre orientations, alors
 * que le fantôme, lui, ne bougerait pas. Avec ce pivot, la case (4,1,4) se projette sur elle-même
 * quelle que soit la rotation, et comme l'emprise au sol est carrée (9×9) la boîte du fantôme est
 * la même pour les quatre orientations — d'où un {@link #previewBox()} sans le moindre calcul de
 * rotation.
 */
public class StarterHouseBlockEntity extends BlockEntity implements MenuProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int SIZE_X = 9;
    public static final int SIZE_Y = 10;
    public static final int SIZE_Z = 9;

    /** Case du schéma qu'occupe le bloc-machine : centre du sol habitable. */
    private static final BlockPos ANCHOR = new BlockPos(4, 1, 4);
    /** Centre de l'emprise au sol, autour duquel tourne le schéma (cf. javadoc de la classe). */
    private static final BlockPos PIVOT = new BlockPos(4, 0, 4);

    /** Orientation dans laquelle le schéma a été dessiné : porte au nord, au milieu du mur. */
    private static final Direction TEMPLATE_FRONT = Direction.NORTH;

    public static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "starter_house");

    public StarterHouseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STARTER_HOUSE.get(), pos, state);
    }

    // ----- Géométrie (partagée par le fantôme côté client et la pose côté serveur) -----

    /** Coin du schéma (case locale 0,0,0) dans le monde. */
    private BlockPos origin() {
        return getBlockPos().subtract(ANCHOR);
    }

    /**
     * Emprise complète de la maison. Identique pour les quatre orientations : l'emprise au sol est
     * carrée et le pivot en est le centre (cf. javadoc de la classe).
     */
    public AABB previewBox() {
        BlockPos o = origin();
        return new AABB(
                o.getX(), o.getY(), o.getZ(),
                o.getX() + SIZE_X, o.getY() + SIZE_Y, o.getZ() + SIZE_Z
        );
    }

    /** Rotation à appliquer au schéma pour que sa façade ({@link #TEMPLATE_FRONT}) parte vers {@code facing}. */
    private static Rotation rotationTo(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE; // NORTH, et les directions verticales que FACING ne prend jamais
        };
    }

    // ----- Construction -----

    /**
     * Matérialise la maison et consomme le bloc. Tout ce qui se trouve dans l'emprise est écrasé :
     * c'est le contrat annoncé au joueur par le fantôme et par l'avertissement de l'interface, et
     * c'est aussi ce qui rend la pose instantanée (aucune file d'attente, aucun matériau à puiser,
     * rien à reprendre après un rechargement de chunk — contrairement au bloc de contrôle).
     *
     * @return {@code false} si le schéma est introuvable, auquel cas le bloc n'est pas consommé.
     */
    public boolean build(ServerLevel level) {
        StructureTemplate template = level.getStructureManager().get(TEMPLATE).orElse(null);
        if (template == null) {
            // Ne devrait arriver que si les ressources du mod sont incomplètes : on préfère laisser
            // le bloc en place plutôt que de le détruire sans rien construire en échange.
            LOGGER.error("Schéma de maison de départ introuvable : {}", TEMPLATE);
            return false;
        }
        Vec3i size = template.getSize();
        if (size.getX() != SIZE_X || size.getY() != SIZE_Y || size.getZ() != SIZE_Z) {
            // SIZE_X/Y/Z, ANCHOR et PIVOT sont dérivés à la main de starter_house.nbt (cf. javadoc de
            // la classe) : un ré-export au gabarit différent romprait silencieusement le fantôme et le
            // texte d'emprise du GUI. On n'annule pas la pose pour autant — placeInWorld() se base sur
            // le schéma réel, pas sur ces constantes, donc la construction elle-même reste correcte —
            // mais on le signale bruyamment plutôt que de laisser le fantôme mentir sans prévenir.
            LOGGER.error("Schéma de maison de départ {}×{}×{} ne correspond plus aux constantes {}×{}×{} attendues",
                    size.getX(), size.getY(), size.getZ(), SIZE_X, SIZE_Y, SIZE_Z);
        }

        // Tout capturer AVANT de retirer le bloc : passé removeBlock, ce BlockEntity est détaché du
        // monde et son BlockState n'est plus une source fiable.
        BlockPos origin = origin();
        BlockPos self = getBlockPos();
        Rotation rotation = rotationTo(getBlockState().getValue(StarterHouseBlock.FACING));

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setRotationPivot(PIVOT)
                // Le schéma a beau être nettoyé de ses entités (cf. tools/sanitize_starter_house.py),
                // on le dit explicitement : un ré-export non nettoyé ne doit pas lâcher un
                // porte-armure moddé dans le monde.
                .setIgnoreEntities(true);

        // Le bloc est DANS l'emprise (case 4,1,4) : la pose l'écraserait de toute façon. On le retire
        // d'abord pour que ce soit franc, et pour ne pas laisser un BlockEntity orphelin derrière.
        level.removeBlock(self, false);
        // UPDATE_CLIENTS seul (et pas UPDATE_ALL) : c'est le drapeau qu'emploie le bloc de structure
        // vanilla pour la même opération. Ajouter UPDATE_NEIGHBORS déclencherait une mise à jour de
        // voisinage pour CHACUN des ~470 blocs, au fil d'une maison encore à moitié posée — boutons,
        // porte et métier à tisser recevant des notifications dans un état intermédiaire. Inutile, en
        // plus : tant que {@code setKnownShape} reste faux, {@code placeInWorld} termine de toute façon
        // par une passe unique de {@code updateFromNeighbourShapes} + {@code blockUpdated} sur tous les
        // blocs posés, maison complète — le bon état final, en une fois au lieu de 470.
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        level.playSound(null, self, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        return true;
    }

    // ----- Menu -----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.turnkey_factory.starter_house");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new StarterHouseMenu(id, inv, this);
    }
}
