package dev.aurelien.prefab.build;

/**
 * Palette d'un bâtiment : un jeu de blocs (texturé) par module structurel. Changer de style suffit
 * à retexturer l'ensemble sans toucher à l'algorithme de composition.
 */
public record BuildStyle(
        Palette floor,        // sol intérieur
        Palette foundation,   // soubassement (périmètre du sol + 1re rangée de mur)
        Palette wall,         // corps de mur (texturé)
        Palette pillar,       // angles (piliers verticaux)
        Palette cornice,      // corniche (coiffe sous le toit)
        Palette roof,         // toiture plate (dalle) ; sous un toit pentu : pignons/parapets
        Palette roofStair,    // tuiles en escalier des pentes (toit pentu), bloc contrastant
        Palette roofRidge,    // faîtage (bloc plein contrastant, au sommet des pentes)
        Palette roofBeam,     // poutres de plafond (toit pentu) pour accrocher les lanternes
        Palette trimStair,    // escalier d'accent : coiffe de colonne (toit pentu), corbeau
        Palette trimSlab,     // dalle d'accent : coiffe d'angle de colonne
        Palette parapetWall,  // muret (WallBlock) du parapet de toit plat
        Palette window,       // vitrage des fenêtres
        Palette lamp          // éclairage plafond
) {}
