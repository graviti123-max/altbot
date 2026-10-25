package pl.altbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Panel AltBot: zakladki Ustawienia / Podglad / Czat. */
public class BotScreen extends Screen {
    private static final int TAB_SETTINGS = 0, TAB_VIEW = 1, TAB_CHAT = 2;
    private static int tab = TAB_SETTINGS;

    private record Label(String text, int x, int y) {}

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private EditBox server, username, command, sellItems, keepItems, sellCommand;

    public BotScreen(Screen parent) {
        super(Component.literal("AltBot"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        int cx = this.width / 2;

        addTab("Ustawienia", cx - 154, TAB_SETTINGS);
        addTab("Podglad", cx - 50, TAB_VIEW);
        addTab("Czat", cx + 54, TAB_CHAT);

        switch (tab) {
            case TAB_VIEW -> initView();
            case TAB_CHAT -> initChat();
            default -> initSettings();
        }
    }

    private void addTab(String name, int x, int index) {
        Button b = Button.builder(Component.literal((tab == index ? "> " : "") + name), btn -> {
            if (tab == index) return;
            if (tab == TAB_SETTINGS) saveFields();
            tab = index;
            rebuildWidgets();
        }).bounds(x, 6, 100, 20).build();
        addRenderableWidget(b);
    }

    // ------------------------------------------------------------ ustawienia

    private void initSettings() {
        BotConfig.Data d = BotConfig.data;
        int colW = 200;
        int gap = 12;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int top = 34;
        int step = 36;

        server = field(left, top, colW, "Serwer", "mc.example.pl", d.server);
        username = field(left, top + step, colW, "Nick (konto non-premium)", "np. Bot123", d.username);

        addRenderableWidget(Button.builder(Component.literal("Polacz"), b -> connect())
                .bounds(left, top + step * 2, colW / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rozlacz"), b -> AccountSwitcher.disconnect())
                .bounds(left + colW / 2 + 2, top + step * 2, colW / 2 - 2, 20).build());

        addRenderableWidget(Button.builder(bigHudLabel(), b -> {
            BotConfig.data.bigHud = !BotConfig.data.bigHud;
            b.setMessage(bigHudLabel());
            BotConfig.save();
        }).bounds(left, top + step * 3, colW, 20).build());

        keepItems = field(right, top, colW, "ZOSTAW w ekwipunku (bot tego nie sprzeda)", "netherite,shulker", d.keepItems);
        sellItems = field(right, top + step, colW, "SPRZEDAJ (bot sprzeda tylko to)", "cactus", d.sellItems);
        sellCommand = field(right, top + step * 2, colW, "Komenda sprzedazy ({item} = nazwa przedmiotu)", "sell", d.sellCommand);

        addRenderableWidget(Button.builder(autoSellLabel(), b -> {
            BotConfig.data.autoSell = !BotConfig.data.autoSell;
            b.setMessage(autoSellLabel());
            BotConfig.save();
        }).bounds(right, top + step * 3, colW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Zapisz i zamknij"), b -> {
            saveFields();
            onClose();
        }).bounds(this.width / 2 - 100, top + step * 4 + 6, 200, 20).build());
    }

    private EditBox field(int x, int y, int w, String label, String hint, String value) {
        labels.add(new Label(label, x, y));
        EditBox b = new EditBox(this.font, x, y + 10, w, 20, Component.literal(label));
        b.setMaxLength(128);
        b.setHint(Component.literal(hint));
        b.setValue(value == null ? "" : value);
        return addRenderableWidget(b);
    }

    private Component autoSellLabel() {
        return Component.literal("Auto-sell: " + (BotConfig.data.autoSell ? "WLACZONE" : "wylaczone"));
    }

    private Component bigHudLabel() {
        return Component.literal("Duzy HUD w grze: " + (BotConfig.data.bigHud ? "WLACZONY" : "wylaczony"));
    }

    private void saveFields() {
        if (server == null) return;
        BotConfig.data.server = server.getValue().trim();
        BotConfig.data.username = username.getValue().trim();
        BotConfig.data.sellItems = sellItems.getValue().trim();
        BotConfig.data.keepItems = keepItems.getValue().trim();
        BotConfig.data.sellCommand = sellCommand.getValue().trim();
        BotConfig.save();
    }

    private void connect() {
        saveFields();
        String s = BotConfig.data.server;
        String u = BotConfig.data.username;
        if (s.isBlank() || u.isBlank()) return;
        AltBotClient.hudPassword = BotConfig.hasPassword(s, u) ? BotConfig.passwordFor(s, u) : "";
        AltBotClient.hudStatus = "Lacze z " + s + " jako " + u;
        AccountSwitcher.connect(s, u);
    }

    // ------------------------------------------------------------ podglad i czat

    private void initView() {
        addTpaButtons(this.height - 30);
    }

    private void initChat() {
        int cx = this.width / 2;
        int y = this.height - 54;
        command = new EditBox(this.font, cx - 200, y, 340, 20, Component.literal("Czat / komenda"));
        command.setMaxLength(256);
        command.setHint(Component.literal("Czat albo komenda, np. /pay nick 12k"));
        addRenderableWidget(command);
        addRenderableWidget(Button.builder(Component.literal("Wyslij"), b -> sendCommand())
                .bounds(cx + 146, y, 54, 20).build());
        addTpaButtons(this.height - 28);
    }

    private void addTpaButtons(int y) {
        int cx = this.width / 2;
        addRenderableWidget(Button.builder(Component.literal("Przyjmij TPA (/tpaccept)"),
                b -> AltBotClient.sendServerCommand("tpaccept")).bounds(cx - 200, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Odrzuc TPA (/tpdeny)"),
                b -> AltBotClient.sendServerCommand("tpdeny")).bounds(cx - 46, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zamknij"), b -> onClose())
                .bounds(cx + 110, y, 90, 20).build());
    }

    /** "/pay nick 12k" -> "/pay nick 12000". Tekst bez "/" idzie na czat. */
    private void sendCommand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || command == null) return;
        String text = command.getValue().trim();
        if (text.isEmpty()) return;

        if (text.startsWith("/")) {
            String[] parts = text.substring(1).split("\\s+");
            if (parts.length >= 3 && parts[0].equalsIgnoreCase("pay")) {
                parts[2] = BotConfig.expandAmount(parts[2]);
            }
            mc.getConnection().sendCommand(String.join(" ", parts));
        } else {
            mc.getConnection().sendChat(text);
        }
        command.setValue("");
    }

    // ------------------------------------------------------------ rysowanie

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        switch (tab) {
            case TAB_VIEW -> renderView(g);
            case TAB_CHAT -> renderChat(g);
            default -> {
                for (Label l : labels) {
                    g.drawString(this.font, l.text(), l.x(), l.y(), 0xFFAAAAAA, false);
                }
            }
        }

        if (System.currentTimeMillis() < AltBotClient.tpaUntil) {
            g.drawCenteredString(this.font, "TPA: " + AltBotClient.tpaText, this.width / 2, 32, 0xFFFF5555);
        }
        if (!AltBotClient.hudPassword.isEmpty()) {
            g.drawCenteredString(this.font, "Haslo: " + AltBotClient.hudPassword, this.width / 2, this.height - 12, 0xFFFFFF55);
        }
    }

    private void renderView(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int left = this.width / 2 - 210;
        int y = 44;
        for (String line : HudInfo.lines(mc)) {
            g.drawString(this.font, line, left, y, 0xFFFFFFFF, false);
            y += 12;
        }

        if (mc.player == null) return;
        int gx = this.width / 2 + 20;
        int gy = 54;
        g.drawString(this.font, "Ekwipunek", gx, 42, 0xFFAAAAAA, false);

        Inventory inv = mc.player.getInventory();
        // 3 rzedy ekwipunku (sloty 9-35), potem hotbar (0-8)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) drawSlot(g, inv.getItem(9 + row * 9 + col), gx + col * 18, gy + row * 18);
        }
        for (int col = 0; col < 9; col++) drawSlot(g, inv.getItem(col), gx + col * 18, gy + 3 * 18 + 6);

        int ly = gy + 4 * 18 + 14;
        g.fill(gx, ly, gx + 8, ly + 8, 0xFF00AA00);
        g.drawString(this.font, "do sprzedazy", gx + 12, ly, 0xFFFFFFFF, false);
        g.fill(gx + 90, ly, gx + 98, ly + 8, 0xFF3366FF);
        g.drawString(this.font, "zostaw", gx + 102, ly, 0xFFFFFFFF, false);
    }

    private void drawSlot(GuiGraphics g, ItemStack stack, int x, int y) {
        g.fill(x, y, x + 17, y + 17, 0xAA000000);
        if (stack.isEmpty()) return;
        if (AutoSeller.isKept(stack)) g.fill(x, y, x + 17, y + 17, 0xAA3366FF);
        else if (AutoSeller.isSellable(stack)) g.fill(x, y, x + 17, y + 17, 0xAA00AA00);
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(this.font, stack, x + 1, y + 1);
    }

    private void renderChat(GuiGraphics g) {
        int left = this.width / 2 - 200;
        int top = 34;
        int bottom = this.height - 62;
        int rows = Math.max(1, (bottom - top) / 10);
        g.fill(left - 4, top - 2, left + 404, bottom + 2, 0x90000000);

        List<String> lines = ChatLog.last(rows);
        int y = bottom - lines.size() * 10;
        for (String line : lines) {
            g.drawString(this.font, this.font.plainSubstrByWidth(line, 396), left, y, 0xFFFFFFFF, false);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        saveFields();
        Minecraft.getInstance().setScreen(parent);
    }
}
