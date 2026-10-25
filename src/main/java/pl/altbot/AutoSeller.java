package pl.altbot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Auto-sprzedaz.
 * Tryb 1: komenda bez {item} (np. "sell") - otwiera okno sprzedazy, mod przenosi do niego
 *         tylko przedmioty z listy "Sprzedawaj" (pomija te z listy "Zostaw") i zamyka okno.
 * Tryb 2: komenda z {item} (np. "sell {item}") - osobna komenda dla kazdego typu przedmiotu.
 */
public final class AutoSeller {
    private enum State { IDLE, WAIT_GUI, SELLING }

    private static State state = State.IDLE;
    private static int timer = 100;
    private static int stepDelay = 0;
    private static final Deque<String> pending = new ArrayDeque<>();

    private AutoSeller() {}

    public static void reset() {
        state = State.IDLE;
        timer = 100;
        pending.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) return;
        if (timer > 0) timer--;

        switch (state) {
            case IDLE -> idle(mc);
            case WAIT_GUI -> waitGui(mc);
            case SELLING -> selling(mc);
        }
    }

    private static void idle(Minecraft mc) {
        String cmd = BotConfig.data.sellCommand.trim().replaceFirst("^/", "");
        if (cmd.isEmpty()) return;

        // tryb 2: kolejka komend per przedmiot
        if (!pending.isEmpty()) {
            if (stepDelay > 0) { stepDelay--; return; }
            mc.getConnection().sendCommand(pending.poll());
            stepDelay = 15;
            return;
        }

        if (timer > 0) return;
        timer = 200; // sprawdzaj co 10 s

        Set<String> found = sellableIds(mc);
        if (found.isEmpty()) return;

        if (cmd.contains("{item}")) {
            for (String id : found) pending.add(cmd.replace("{item}", id));
            AltBotClient.hudStatus = "Sprzedaje: " + String.join(", ", found);
        } else {
            mc.getConnection().sendCommand(cmd);
            state = State.WAIT_GUI;
            timer = 60; // max 3 s na otwarcie okna
            AltBotClient.hudStatus = "Sprzedaje: " + String.join(", ", found);
        }
    }

    private static void waitGui(Minecraft mc) {
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            state = State.SELLING;
            stepDelay = 5;
        } else if (timer == 0) {
            state = State.IDLE;
            timer = 200;
        }
    }

    private static void selling(Minecraft mc) {
        if (mc.player.containerMenu == mc.player.inventoryMenu) {
            state = State.IDLE;
            timer = 200;
            return;
        }
        if (stepDelay > 0) { stepDelay--; return; }

        for (Slot slot : mc.player.containerMenu.slots) {
            if (!(slot.container instanceof Inventory)) continue;
            if (slot.getContainerSlot() >= 36) continue;
            ItemStack stack = slot.getItem();
            if (isSellable(stack)) {
                mc.gameMode.handleInventoryMouseClick(
                        mc.player.containerMenu.containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
                stepDelay = 2;
                return;
            }
        }
        // nic wiecej do przeniesienia - zamknij okno (serwer zwykle wtedy sprzedaje)
        mc.player.closeContainer();
        state = State.IDLE;
        timer = 200;
    }

    private static Set<String> sellableIds(Minecraft mc) {
        Set<String> ids = new LinkedHashSet<>();
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (isSellable(stack)) ids.add(idOf(stack));
        }
        return ids;
    }

    public static boolean isSellable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = idOf(stack);
        return matches(id, BotConfig.data.sellItems) && !matches(id, BotConfig.data.keepItems);
    }

    public static boolean isKept(ItemStack stack) {
        return !stack.isEmpty() && matches(idOf(stack), BotConfig.data.keepItems);
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static boolean matches(String id, String csv) {
        for (String n : csv.toLowerCase(Locale.ROOT).split("[,;\\s]+")) {
            if (!n.isBlank() && id.contains(n)) return true;
        }
        return false;
    }
}
