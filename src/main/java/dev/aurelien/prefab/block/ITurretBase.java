package dev.aurelien.prefab.block;

/**
 * Marqueur porté par les deux socles de tourelle : {@link TurretBaseBlock} (charbon) et le socle
 * cinétique de compat/create. Sert uniquement à répondre « ce bloc est-il un socle ? » depuis
 * {@link TurretWeaponBlock#canSurvive}, côté commun.
 * <p>
 * C'est une interface et non un test {@code state.is(ModBlocks.TURRET_BASE)} élargi au socle Create :
 * {@link TurretWeaponBlock} vit dans un paquet non gardé, et y nommer le bloc Create obligerait le
 * vérificateur à résoudre {@code KineticBlock} même sans Create chargé — exactement ce que la javadoc
 * de {@code CreateKineticContent} interdit. Un marqueur sans aucun type Create coupe court au
 * problème.
 */
public interface ITurretBase {
}
