package dev.aurelien.prefab.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config serveur unique du mod (fichier {@code turnkey_factory-server.toml}, dans le dossier de
 * config du monde — enregistrée en {@code ModConfig.Type.SERVER} depuis {@code PrefabMod}, donc
 * synchronisée automatiquement au client à la connexion : pas besoin d'un payload réseau dédié pour
 * que les écrans lisent les mêmes bornes que le serveur qui les fera respecter).
 * <p>
 * Les {@code static final} du reste du code (dimensions par défaut, pas d'incrément…) ne bougent pas
 * d'ici : seules les valeurs qu'un admin de serveur a une vraie raison de retoucher — équilibrage
 * tourelle et bornes réglables des machines de terrain — sont exposées.
 */
public final class PrefabServerConfig {
    private PrefabServerConfig() {}

    public static final ModConfigSpec SPEC;

    // ----- Compat MineColonies -----
    public static final ModConfigSpec.BooleanValue MINECOLONIES_TURRET_FIX;

    // ----- Tourelle -----
    public static final ModConfigSpec.DoubleValue TURRET_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.IntValue TURRET_FIRE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue TURRET_MIN_RANGE;
    public static final ModConfigSpec.IntValue TURRET_MAX_RANGE;
    public static final ModConfigSpec.IntValue TURRET_FLAMETHROWER_LAVA_PER_SHOT;

    // ----- Bornes des machines de terrain -----
    public static final ModConfigSpec.IntValue LEVELER_MIN_RANGE;
    public static final ModConfigSpec.IntValue LEVELER_MAX_RANGE;
    public static final ModConfigSpec.IntValue TEXTURIZER_MIN_RADIUS;
    public static final ModConfigSpec.IntValue TEXTURIZER_MAX_RADIUS;
    public static final ModConfigSpec.IntValue LAMPLIGHTER_MIN_RANGE;
    public static final ModConfigSpec.IntValue LAMPLIGHTER_MAX_RANGE;
    public static final ModConfigSpec.IntValue LAMPLIGHTER_MIN_SPACING;
    public static final ModConfigSpec.IntValue LAMPLIGHTER_MAX_SPACING;
    public static final ModConfigSpec.IntValue CONTROLLER_MIN_SIZE;
    public static final ModConfigSpec.IntValue CONTROLLER_MAX_HORIZONTAL;
    public static final ModConfigSpec.IntValue CONTROLLER_MAX_HEIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("compat");
        MINECOLONIES_TURRET_FIX = builder
                .comment(
                        "Bypasses MineColonies' anti-turret guard: raiders treat any damage with no",
                        "identifiable attacker (which is what turret shots are) as environmental damage,",
                        "capped and put on cooldown, which can never be lethal.",
                        "true = turrets hurt and kill MineColonies raiders normally.",
                        "No effect if MineColonies is not installed.")
                .define("mineColoniesTurretFix", true);
        builder.pop();

        builder.push("turret");
        TURRET_DAMAGE_MULTIPLIER = builder
                .comment("Multiplier applied to every turret shot (machine gun and flamethrower).")
                .defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        TURRET_FIRE_INTERVAL_TICKS = builder
                .comment("Fire rate of the coal-powered turret, in ticks (20 = 1 shot/second).",
                        "Does not affect the Create variant, whose fire rate follows rotation speed.")
                .defineInRange("fireIntervalTicks", 20, 1, 200);
        TURRET_MIN_RANGE = builder
                .comment("Minimum range settable on a turret.")
                .defineInRange("minRange", 4, 1, 128);
        TURRET_MAX_RANGE = builder
                .comment("Maximum range settable on a turret.")
                .defineInRange("maxRange", 32, 1, 128);
        TURRET_FLAMETHROWER_LAVA_PER_SHOT = builder
                .comment("Lava consumed (in mB) per flamethrower shot.")
                .defineInRange("flamethrowerLavaPerShot", 125, 1, 1000);
        builder.pop();

        builder.push("terrainMachineBounds");
        LEVELER_MIN_RANGE = builder
                .comment("Minimum range settable on the leveler.")
                .defineInRange("levelerMinRange", 4, 1, 256);
        LEVELER_MAX_RANGE = builder
                .comment("Maximum range settable on the leveler.")
                .defineInRange("levelerMaxRange", 64, 1, 256);
        TEXTURIZER_MIN_RADIUS = builder
                .comment("Minimum radius settable on the texturizer.")
                .defineInRange("texturizerMinRadius", 2, 1, 256);
        TEXTURIZER_MAX_RADIUS = builder
                .comment("Maximum radius settable on the texturizer.")
                .defineInRange("texturizerMaxRadius", 64, 1, 256);
        LAMPLIGHTER_MIN_RANGE = builder
                .comment("Minimum range settable on the lamplighter.")
                .defineInRange("lamplighterMinRange", 8, 1, 256);
        LAMPLIGHTER_MAX_RANGE = builder
                .comment("Maximum range settable on the lamplighter.")
                .defineInRange("lamplighterMaxRange", 64, 1, 256);
        LAMPLIGHTER_MIN_SPACING = builder
                .comment("Minimum spacing settable between lamp posts.")
                .defineInRange("lamplighterMinSpacing", 6, 1, 128);
        LAMPLIGHTER_MAX_SPACING = builder
                .comment("Maximum spacing settable between lamp posts.")
                .defineInRange("lamplighterMaxSpacing", 32, 1, 128);
        CONTROLLER_MIN_SIZE = builder
                .comment("Minimum size (width/length/height) settable on the controller.")
                .defineInRange("controllerMinSize", 7, 3, 256);
        CONTROLLER_MAX_HORIZONTAL = builder
                .comment("Maximum width/length settable on the controller (always rounded down to odd).")
                .defineInRange("controllerMaxHorizontal", 63, 3, 511);
        CONTROLLER_MAX_HEIGHT = builder
                .comment("Maximum height settable on the controller.")
                .defineInRange("controllerMaxHeight", 64, 3, 512);
        builder.pop();

        SPEC = builder.build();
    }
}
