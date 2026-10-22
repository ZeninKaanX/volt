package dev.volt.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Native editing, clipboard and keyboard semantics; scale-aware antialiased presentation. */
public final class SmoothEditBox extends EditBox {
    private final String hint;
    private final boolean secret;
    private int firstVisible;
    public SmoothEditBox(Font font, int x, int y, int w, int h, Component hint, boolean secret) {
        super(font, x, y, w, h, hint); this.hint = hint.getString(); this.secret = secret;
    }
    @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        VoltTheme.box(g, getX(), getY(), width, height, isFocused() ? 0xFF69765E : 0xFF333B47);
        VoltTheme.box(g, getX()+1, getY()+1, width-2, height-2, 0xFF12171E);
        String value = secret ? "•".repeat(getValue().length()) : getValue();
        int cursor = Math.min(getCursorPosition(), value.length()), start = 0;
        while (start < cursor && SmoothRaster.width(value.substring(start, cursor), 12, false) > width - 24) start++;
        firstVisible=start;
        g.enableScissor(getX()+8, getY()+2, getX()+width-8, getY()+height-2);
        int textY = getY()+(height-17)/2;
        if (value.isEmpty() && !isFocused()) SmoothRaster.text(g, hint, getX()+10, textY, 12, VoltTheme.MUTED, false);
        else SmoothRaster.text(g, value.substring(start), getX()+10, textY, 12, VoltTheme.WHITE, false);
        if (isFocused() && !getHighlighted().isEmpty()) {
            int selectionWidth = SmoothRaster.width(secret ? "•".repeat(getHighlighted().length()) : getHighlighted(), 12, false);
            int cursorX = getX()+10+SmoothRaster.width(value.substring(start,cursor),12,false);
            g.fill(Math.max(getX()+8,cursorX-selectionWidth), getY()+5, cursorX, getY()+height-5, 0x555CA6E8);
        }
        if (isFocused() && System.currentTimeMillis()/500%2 == 0) {
            int cx = getX()+10+SmoothRaster.width(value.substring(start,cursor),12,false);
            g.fill(cx, getY()+7, cx+1, getY()+height-7, VoltTheme.ACCENT);
        }
        g.disableScissor();
    }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event, doubleClick);
        if (!doubleClick) {
            String value = secret ? "•".repeat(getValue().length()) : getValue();
            int target = (int) event.x()-getX()-10, nearest=Math.min(firstVisible,value.length());
            while (nearest < value.length() && SmoothRaster.width(value.substring(firstVisible,nearest+1),12,false) < target) nearest++;
            setCursorPosition(nearest); setHighlightPos(nearest);
        }
    }
}
