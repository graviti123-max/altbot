package pl.altbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class AltBotClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("altbot");

    /** Ostatnie haslo pokazywane w HUD (widzisz je tylko Ty). */
    public static String hudPassword = "";
    public static String hudStatus = "";

    private int authCooldown = 0;
    private int sellCooldown = 0;

    @Override
    public void onInitializeClient() {
        BotConfig.load();

        // /altbot otwiera panel
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("altbot").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreen(new BotScreen(mc.screen)));
                    return 1;
                })));

        // Przycisk "AltBot" na ekranie tytulowym i liscie serwerow
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("AltBot"),
                        b -> client.setScreen(new BotScreen(screen))).bounds(5, 5, 60, 20).build());
            }
        });

        // Wykrywanie prosb o rejestracje / logowanie w czacie
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> onMessage(message.getString()));

        // Skroty do TPA (przydatne, gdy serwer nie pokazuje okienka)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ta").executes(ctx -> {
                sendServerCommand("tpaccept");
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("td").executes(ctx -> {
                sendServerCommand("tpdeny");
                return 1;
            }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        // Maly HUD z haslem i statusem
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;

            java.util.List<String> lines = new java.util.ArrayList<>();
            if (!hudStatus.isEmpty()) lines.add("[AltBot] " + hudStatus);
            if (!hudPassword.isEmpty()) lines.add("Haslo: " + hudPassword);
            if (BotConfig.data.bigHud) lines.addAll(HudInfo.lines(mc));

            if (!lines.isEmpty()) {
                int w = 0;
                for (String l : lines) w = Math.max(w, mc.font.width(l));
                int h = lines.size() * 10 + 6;
                graphics.fill(2, 2, 2 + w + 8, 2 + h, 0x90000000);
                int y = 5;
                for (int i = 0; i < lines.size(); i++) {
                    int color = 0xFFFFFFFF;
                    String l = lines.get(i);
                    if (l.startsWith("[AltBot]")) color = 0xFF55FF55;
                    else if (l.startsWith("Haslo:")) color = 0xFFFFFF55;
                    graphics.drawString(mc.font, l, 6, y, color, true);
                    y += 10;
                }
            }

            // Alert TPA na srodku gory ekranu
            if (System.currentTimeMillis() < tpaUntil) {
                String text = "TPA: " + tpaText;
                String hint = "/ta = przyjmij   /td = odrzuc   albo AltBot > Czat";
                int w = Math.max(mc.font.width(text), mc.font.width(hint)) + 12;
                int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
                graphics.fill(x, 4, x + w, 30, 0xC0A02020);
                graphics.drawCenteredString(mc.font, text, x + w / 2, 8, 0xFFFFFFFF);
                graphics.drawCenteredString(mc.font, hint, x + w / 2, 19, 0xFFFFFF55);
            }
        });
    }

    /** Ostatnie zapytanie o teleportacje (pokazywane w HUD przez 60 s). */
    public static String tpaText = "";
    public static long tpaUntil = 0;

    private void onMessage(String raw) {
        ChatLog.add(raw);
        String t = raw.toLowerCase(Locale.ROOT);
        boolean tpa = t.contains("tpa") || t.contains("teleport");
        boolean request = t.contains("request") || t.contains("prosb") || t.contains("chce") || t.contains("wants")
                || t.contains("tpaccept") || t.contains("zapros");
        if (tpa && request) {
            tpaText = raw.length() > 60 ? raw.substring(0, 60) + "..." : raw;
            tpaUntil = System.currentTimeMillis() + 60_000;
        }
        handleAuthMessage(raw);
    }

    public static void sendServerCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(command.startsWith("/") ? command.substring(1) : command);
            tpaUntil = 0;
        }
    }

    private void handleAuthMessage(String raw) {
        if (authCooldown > 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return;

        String text = raw.toLowerCase(Locale.ROOT);
        String server = BotConfig.data.server;
        String user = mc.player.getName().getString();
        if (server.isBlank()) return;

        boolean wantsRegister = text.contains("/register") || text.contains("/reg ") || text.contains("zarejestruj");
        boolean wantsLogin = text.contains("/login") || text.contains("/l ") || text.contains("zaloguj");

        if (wantsRegister && !BotConfig.hasPassword(server, user)) {
            String pw = BotConfig.passwordFor(server, user);
            hudPassword = pw;
            hudStatus = "Rejestracja jako " + user;
            mc.getConnection().sendCommand("register " + pw + " " + pw);
            authCooldown = 60;
        } else if (wantsLogin || wantsRegister) {
            String pw = BotConfig.passwordFor(server, user);
            hudPassword = pw;
            hudStatus = "Logowanie jako " + user;
            mc.getConnection().sendCommand("login " + pw);
            authCooldown = 60;
        }
    }

    private void tick(Minecraft mc) {
        if (authCooldown > 0) authCooldown--;
        if (sellCooldown > 0) sellCooldown--;
        if (mc.player == null || mc.getConnection() == null) return;

        // Auto-sell: sprzedaje tylko przedmioty z listy "Sprzedawaj", pomija "Zostaw"
        if (BotConfig.data.autoSell) {
            AutoSeller.tick(mc);
        } else {
            AutoSeller.reset();
        }
    }
}
