package dev.volt.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class VoltButton extends Button {
    private final boolean primary;
    public VoltButton(int x, int y, int w, int h, Component text, boolean primary, Runnable action) {
        super(x, y, w, h, text, ignored -> action.run(), DEFAULT_NARRATION); this.primary = primary;
    }
    @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager){ClipFeedback.click(manager);}
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int bg = !active ? VoltTheme.PANEL : primary ? (isHoveredOrFocused() ? 0xFFE4FFBA : VoltTheme.ACCENT) : (isHoveredOrFocused() ? 0xFF303844 : 0xFF222832);
        VoltTheme.box(g, getX(), getY(), width, height, bg);
        int color = !active ? 0xFF606773 : primary ? VoltTheme.BG : VoltTheme.WHITE;
        VoltTheme.label(g, getMessage(), getX() + (width - SmoothRaster.width(getMessage().getString(), 12, false)) / 2, getY() + (height - 16) / 2, color);
        if (isFocused()) g.fill(getX() + 4, getY() + height - 2, getX() + width - 4, getY() + height - 1, VoltTheme.WHITE);
    }
}
