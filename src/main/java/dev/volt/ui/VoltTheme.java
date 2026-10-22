package dev.volt.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

public final class VoltTheme {
    public static final int BG = 0xFF0B0B0B, PANEL = 0xFF151515, CARD = 0xFF1D1D1D;
    public static final int LINE = 0xFF333333, WHITE = 0xFFF4F5F7, MUTED = 0xFF9B9B9F;
    public static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("volt", "ui"));
    public static MutableComponent text(String key, Object... args) { return Component.translatable(key, args).withStyle(s -> s.withFont(FONT)); }
    public static MutableComponent literal(String value) { return Component.literal(value).withStyle(s -> s.withFont(FONT)); }
    public static final int ACCENT = 0xFFCEF45A;
    public static void label(GuiGraphics g, Component value, int x, int y, int color) { SmoothRaster.text(g, value.getString(), x, y, 12, color, value.getStyle().isBold()); }
    public static void label(GuiGraphics g, String key, int x, int y, int color) { label(g, text(key), x, y, color); }
    public static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        SmoothRaster.rounded(g, x, y, w, h, color);
    }
    public static void image(GuiGraphics g, Identifier id, int x, int y, int w, int h) { g.blit(id, x, y, x + w, y + h, 0, 1, 0, 1); }
    public static String duration(double seconds) { int n = (int) seconds; return "%d:%02d".formatted(n / 60, n % 60); }
}
