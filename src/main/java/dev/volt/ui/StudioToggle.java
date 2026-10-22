package dev.volt.ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

public final class StudioToggle extends Button {
    private final BooleanSupplier enabled;
    public StudioToggle(int x,int y,Component label,BooleanSupplier enabled,Runnable action){super(x,y,38,22,label,b->action.run(),DEFAULT_NARRATION);this.enabled=enabled;}
    @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager){ClipFeedback.click(manager);}
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){boolean on=enabled.getAsBoolean();VoltTheme.box(g,getX(),getY()+2,38,19,on?VoltTheme.ACCENT:0xFF535353);VoltTheme.box(g,getX()+(on?20:3),getY()+4,15,15,on?VoltTheme.BG:0xFFEEEEEE);}
}
