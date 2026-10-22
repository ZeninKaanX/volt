package dev.volt.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Compact menu launcher with a fixed physical footprint at every GUI scale. */
public final class VoltMenuButton extends Button {
    public VoltMenuButton(int screenWidth,Runnable action){
        super(screenWidth-units(112),units(16),units(96),units(38),Component.literal("Volt"),b->action.run(),DEFAULT_NARRATION);
        setTooltip(Tooltip.create(Component.literal("Volt")));
    }
    private static int units(int pixels){return Math.max(1,(int)Math.ceil((double)pixels/Minecraft.getInstance().getWindow().getGuiScale()));}
    @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager){ClipFeedback.click(manager);}
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){
        g.pose().pushMatrix();g.pose().translate((float)getX(),(float)getY());
        float scale=1f/Minecraft.getInstance().getWindow().getGuiScale();g.pose().scale(scale,scale);
        VoltTheme.box(g,0,0,96,38,0xFF8994A3);
        VoltTheme.box(g,1,1,94,36,isHoveredOrFocused()?0xFF3B4554:0xFF242C38);
        SmoothRaster.text(g,"VOLT",(96-SmoothRaster.width("VOLT",16,true))/2,9,16,VoltTheme.WHITE,true);
        g.pose().popMatrix();
    }
}
