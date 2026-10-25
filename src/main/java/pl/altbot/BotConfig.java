package pl.altbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/** Zapisuje ustawienia panelu i hasla wygenerowane dla poszczegolnych serwerow. */
public final class BotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("altbot.json");
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static class Data {
        public String server = "";
        public String username = "";
        public String sellItems = "cactus";
        /** Przedmioty, ktorych bot NIE sprzedaje (zostaja w ekwipunku). */
        public String keepItems = "netherite,shulker";
        /** "sell" = otwiera okno sprzedazy; "sell {item}" = osobna komenda dla kazdego przedmiotu. */
        public String sellCommand = "sell";
        public boolean autoSell = false;
        public boolean bigHud = true;
        /** klucz: "serwer|nick" -> haslo */
        public Map<String, String> passwords = new HashMap<>();
    }

    public static Data data = new Data();

    private BotConfig() {}

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Type type = new TypeToken<Data>() {}.getType();
                Data loaded = GSON.fromJson(Files.readString(FILE), type);
                if (loaded != null) data = loaded;
            }
        } catch (Exception e) {
            AltBotClient.LOGGER.warn("Nie udalo sie wczytac configu", e);
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(data));
        } catch (IOException e) {
            AltBotClient.LOGGER.warn("Nie udalo sie zapisac configu", e);
        }
    }

    public static String keyFor(String server, String username) {
        return server.trim().toLowerCase() + "|" + username.trim();
    }

    /** Zwraca zapisane haslo dla pary serwer+nick albo generuje nowe. */
    public static String passwordFor(String server, String username) {
        String key = keyFor(server, username);
        String pw = data.passwords.get(key);
        if (pw == null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            pw = sb.toString();
            data.passwords.put(key, pw);
            save();
        }
        return pw;
    }

    public static boolean hasPassword(String server, String username) {
        return data.passwords.containsKey(keyFor(server, username));
    }

    /** "12k" -> "12000", "1.5m" -> "1500000", reszta bez zmian. */
    public static String expandAmount(String token) {
        String t = token.toLowerCase().trim();
        try {
            long mult = 1;
            if (t.endsWith("k")) { mult = 1_000L; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("m")) { mult = 1_000_000L; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("b")) { mult = 1_000_000_000L; t = t.substring(0, t.length() - 1); }
            else return token;
            return String.valueOf(Math.round(Double.parseDouble(t) * mult));
        } catch (NumberFormatException e) {
            return token;
        }
    }
}
