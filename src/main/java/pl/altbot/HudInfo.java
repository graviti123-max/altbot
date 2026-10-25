package pl.altbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Zbiera linie ze statystykami - uzywane w duzym HUD i w zakladce "Podglad". */
public final class HudInfo {
    private HudInfo() {}

    public static List<String> lines(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (mc.player == null || mc.level == null) {
            out.add("Brak polaczenia z serwerem");
            return out;
        }

        out.add("Nick: " + mc.player.getName().getString());
        out.add("Serwer: " + (BotConfig.data.server.isBlank() ? "-" : BotConfig.data.server));

        String ping = "?";
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ping = String.valueOf(info.getLatency());
        }
        out.add("Ping: " + ping + " ms");

        out.add(String.format(Locale.ROOT, "HP: %.0f / %.0f   Glod: %d",
                mc.player.getHealth(), mc.player.getMaxHealth(), mc.player.getFoodData().getFoodLevel()));
        out.add("Poziom XP: " + mc.player.experienceLevel);
        out.add(String.format(Locale.ROOT, "Pozycja: %.0f  %.0f  %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        out.add("Wymiar: " + dimensionName(mc));

        Inventory inv = mc.player.getInventory();
        int free = 0, sell = 0, keep = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) { free++; continue; }
            if (AutoSeller.isKept(s)) keep += s.getCount();
            else if (AutoSeller.isSellable(s)) sell += s.getCount();
        }
        out.add("Wolne sloty: " + free + " / 36");
        out.add("Do sprzedazy: " + sell + " szt.");
        out.add("Zostawione: " + keep + " szt.");
        out.add("Auto-sell: " + (BotConfig.data.autoSell ? "WLACZONE" : "wylaczone"));
        return out;
    }

    /** "ResourceKey[minecraft:dimension / minecraft:the_nether]" -> "the_nether" (bez zaleznosci od nazw klas). */
    private static String dimensionName(Minecraft mc) {
        String raw = mc.level.dimension().toString();
        int slash = raw.lastIndexOf('/');
        String part = slash >= 0 ? raw.substring(slash + 1) : raw;
        part = part.replace("]", "").trim();
        int colon = part.indexOf(':');
        return colon >= 0 ? part.substring(colon + 1) : part;
    }
}
