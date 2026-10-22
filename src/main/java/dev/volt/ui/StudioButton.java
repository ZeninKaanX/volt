package dev.volt.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Compact desktop controls with accessible labels and smooth icon paths. */
public final class StudioButton extends Button {
    private String icon;
    private final int style;
    private final boolean iconOnly;
    public StudioButton(int x,int y,int w,int h,Component label,String icon,int style,boolean iconOnly,Runnable action){
        super(x,y,w,h,label,b->action.run(),DEFAULT_NARRATION);this.icon=icon;this.style=style;this.iconOnly=iconOnly;
        setTooltip(Tooltip.create(label));
    }
    public void setIcon(String value){icon=value;}
    @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager){ClipFeedback.click(manager);}
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){
        boolean hover=isHoveredOrFocused();int bg=switch(style){case 2->hover?0xFFE0FF84:VoltTheme.ACCENT;case 3->0xFF343434;case 4->VoltTheme.LINE;case 5->0xFFF2F2F2;default->hover?0xFF303030:style==1?0xFF222222:0;};
        if(bg!=0)VoltTheme.box(g,getX(),getY(),width,height,bg);
        if(style==4)VoltTheme.box(g,getX()+1,getY()+1,width-2,height-2,hover?0xFF303030:VoltTheme.PANEL);
        int color=!active?0xFF686868:style==2||style==5?VoltTheme.BG:VoltTheme.WHITE;
        if(iconOnly){SmoothRaster.icon(g,icon,getX()+(width-20)/2,getY()+(height-20)/2,20,style==3?VoltTheme.ACCENT:color);return;}
        String label=getMessage().getString();int available=width-(icon.isEmpty()?18:40);
        while(label.length()>1&&SmoothRaster.width(label,12,false)>available)label=label.substring(0,label.length()-1);
        int textWidth=SmoothRaster.width(label,12,false),combined=textWidth+(icon.isEmpty()?0:26);
        int x=getX()+(width-combined)/2;
        if(!icon.isEmpty()){SmoothRaster.icon(g,icon,x,getY()+(height-18)/2,18,color);x+=26;}
        SmoothRaster.text(g,label,x,getY()+(height-17)/2,12,color,false);
    }
}
