package dev.aurelien.prefab.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Contournement du garde-fou anti-tourelles de MineColonies. Zéro dépendance compile : la détection
 * se fait sur le namespace du registre de l'entité ciblée, jamais sur une classe MineColonies — sûr
 * à appeler même si MineColonies n'est pas chargé (le namespace ne matchera jamais dans ce cas).
 * <p>
 * MineColonies traite tout dégât dont la {@code DamageSource} n'a pas d'entité vivante attaquante
 * ({@code getEntity() == null} ou non-{@link LivingEntity}) comme un dégât <em>environnemental</em>
 * (lave, chute…) : bloqué pendant un cooldown après chaque coup encaissé, et de toute façon plafonné
 * pour ne jamais faire descendre un raider sous 20-60% de sa vie max (cf. le code source public,
 * {@code AbstractEntityMinecoloniesRaider#hurt}). But annoncé : empêcher de cheeser les raids avec de
 * la lave/des chutes — mais {@code TurretCombat#applyHit} tire justement via
 * {@code damageSources().magic()}, sans attaquant, exactement le cas visé. On contourne en laissant
 * {@code hurt()} s'exécuter d'abord (flash, son, knockback quand MineColonies laisse passer), puis en
 * appliquant nous-mêmes le reliquat directement sur la vie s'il a été bloqué ou plafonné.
 */
public final class MineColoniesCompat {
    private MineColoniesCompat() {}

    /** Vrai si {@code target} vient du namespace {@code minecolonies} (raiders inclus). */
    public static boolean isMineColoniesEntity(LivingEntity target) {
        EntityType<?> type = target.getType();
        return "minecolonies".equals(EntityType.getKey(type).getNamespace());
    }

    /**
     * Applique {@code amount} de dégâts à {@code target}, en garantissant qu'ils passent même si
     * {@code hurt()} les bloque ou les plafonne (cf. javadoc de classe). {@code hurt()} est toujours
     * appelé en premier, pour conserver le feedback vanilla (flash, son, knockback) sur la part que
     * MineColonies laisse passer telle quelle.
     */
    public static void forceDamage(LivingEntity target, DamageSource cause, float amount) {
        if (!target.isAlive()) return;

        float before = target.getHealth();
        target.hurt(cause, amount);
        float applied = before - target.getHealth();
        float missing = amount - applied;
        if (missing <= 0 || !target.isAlive()) return;

        float remaining = target.getHealth() - missing;
        if (remaining > 0) {
            target.setHealth(remaining);
        } else {
            target.setHealth(0);
            target.die(cause);
        }
    }
}
