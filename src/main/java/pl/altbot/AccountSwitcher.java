package pl.altbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import pl.altbot.mixin.MinecraftAccessor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Podmienia nick (konto offline) i laczy z serwerem. */
public final class AccountSwitcher {
    private static User originalUser;

    private AccountSwitcher() {}

    public static void connect(String address, String username) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftAccessor acc = (MinecraftAccessor) mc;

        if (originalUser == null) originalUser = acc.altbot$getUser();

        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        User offline = new User(username, offlineUuid, "0", Optional.empty(), Optional.empty());
        acc.altbot$setUser(offline);

        ServerAddress parsed = ServerAddress.parseString(address);
        ServerData data = new ServerData("AltBot", address, ServerData.Type.OTHER);

        if (mc.level != null) {
            mc.level.disconnect(net.minecraft.network.chat.Component.literal("AltBot"));
            mc.disconnect(new TitleScreen(), false);
        }
        ConnectScreen.startConnecting(new TitleScreen(), mc, parsed, data, false, null);
    }

    /** Przywraca oryginalne (premium) konto. */
    public static void restoreOriginal() {
        if (originalUser != null) {
            ((MinecraftAccessor) Minecraft.getInstance()).altbot$setUser(originalUser);
        }
    }

    public static void disconnect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.disconnect(net.minecraft.network.chat.Component.literal("AltBot"));
            mc.disconnect(new TitleScreen());
        }
    }
}
