package dev.aurelien.prefab.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Réseau d'inventaires liés à un bloc (flood-fill BFS 6-directions sur les {@link IItemHandler}
 * voisins) : détection des inventaires connectés, comptage et extraction d'items. Partagé entre le
 * bloc de contrôle (matériaux de construction) et la niveleuse (matériau de remblai).
 */
public final class InventoryNetwork {
    private InventoryNetwork() {}

    private static final int MAX_LINKED = 256; // garde-fou flood-fill

    /**
     * Flood-fill BFS 6-directions depuis {@code origin} : tout inventaire adjacent, puis tout
     * inventaire adjacent à un inventaire trouvé, récursivement. Recalcul complet à chaque appel →
     * casser un maillon déconnecte automatiquement la suite. Met à jour {@code linked} en place et
     * renvoie {@code true} si l'ensemble a changé. Fait apparaître des particules entre {@code origin}
     * et chaque inventaire nouvellement détecté.
     */
    public static boolean rescan(ServerLevel server, BlockPos origin, List<BlockPos> linked) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        visited.add(origin);
        for (Direction d : Direction.values()) {
            queue.add(origin.relative(d));
        }

        while (!queue.isEmpty() && found.size() < MAX_LINKED) {
            BlockPos p = queue.poll();
            if (!visited.add(p)) continue;
            if (!server.isLoaded(p)) continue;
            IItemHandler handler = server.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (handler == null) continue;
            found.add(p.immutable());
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!visited.contains(n)) queue.add(n);
            }
        }

        boolean changed = false;
        for (BlockPos p : found) {
            if (!linked.contains(p)) {
                spawnLinkParticles(server, origin, p);
                changed = true;
            }
        }
        if (changed || !found.equals(new LinkedHashSet<>(linked))) {
            linked.clear();
            linked.addAll(found);
            return true;
        }
        return false;
    }

    private static void spawnLinkParticles(ServerLevel server, BlockPos origin, BlockPos inventory) {
        spawnPuff(server, inventory);
        spawnPuff(server, origin);
    }

    private static void spawnPuff(ServerLevel server, BlockPos p) {
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5,
                8, 0.3, 0.3, 0.3, 0.0);
    }

    /**
     * Quantité totale d'items déjà stockés dans les inventaires liés qui satisfont {@code eligible},
     * tous types confondus — sert à estimer combien de remblai est immédiatement disponible sans
     * attendre le débris du dégagement (cf. niveleuse).
     */
    public static int countEligible(ServerLevel server, List<BlockPos> linked, Predicate<Item> eligible) {
        int total = 0;
        for (BlockPos p : linked) {
            if (!server.isLoaded(p)) continue;
            IItemHandler handler = server.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && eligible.test(stack.getItem())) total += stack.getCount();
            }
        }
        return total;
    }

    public static void extract(ServerLevel server, List<BlockPos> linked, Item item, int amount) {
        extractEligible(server, linked, i -> i == item, amount);
    }

    /**
     * Comme {@link #extract}, mais prélève sur n'importe quel item satisfaisant {@code eligible} —
     * utile pour un coût « famille » (ex. n'importe quelle planche/rondin plutôt qu'une essence figée).
     */
    public static void extractEligible(ServerLevel server, List<BlockPos> linked, Predicate<Item> eligible, int amount) {
        int remaining = amount;
        for (BlockPos p : linked) {
            if (remaining <= 0) return;
            if (!server.isLoaded(p)) continue;
            IItemHandler handler = server.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && eligible.test(stack.getItem())) {
                    ItemStack taken = handler.extractItem(slot, remaining, false);
                    remaining -= taken.getCount();
                }
            }
        }
    }

    /**
     * Insère {@code stack} dans les inventaires liés (premier emplacement libre trouvé). Renvoie le
     * reliquat non absorbé (vide si tout a pu être rangé) — sert à recycler les débris retirés par la
     * niveleuse : ils redeviennent disponibles comme remblai au lieu d'être perdus.
     */
    public static ItemStack insert(ServerLevel server, List<BlockPos> linked, ItemStack stack) {
        ItemStack remaining = stack;
        for (BlockPos p : linked) {
            if (remaining.isEmpty()) break;
            if (!server.isLoaded(p)) continue;
            IItemHandler handler = server.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (handler == null) continue;
            remaining = ItemHandlerHelper.insertItemStacked(handler, remaining, false);
        }
        return remaining;
    }

    /**
     * Tire un item au hasard parmi ceux présents dans les inventaires liés qui satisfont
     * {@code eligible}, pondéré par la quantité disponible (plus il y en a, plus il a de chances
     * d'être choisi) — sert à répartir un matériau de remblai composite (terre/sable/gravier…) sur
     * la surface plutôt que de toujours piocher le même type. Renvoie {@code null} si rien d'éligible
     * n'est disponible.
     */
    public static Item pickWeightedRandom(ServerLevel server, List<BlockPos> linked,
                                           Predicate<Item> eligible, RandomSource random) {
        Map<Item, Integer> tally = new LinkedHashMap<>();
        for (BlockPos p : linked) {
            if (!server.isLoaded(p)) continue;
            IItemHandler handler = server.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !eligible.test(stack.getItem())) continue;
                tally.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        int total = tally.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return null;
        int r = random.nextInt(total);
        for (Map.Entry<Item, Integer> e : tally.entrySet()) {
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        return null; // inatteignable : total couvre exactement la somme des poids
    }
}
