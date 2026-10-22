package dev.volt.ui;

import dev.volt.VoltClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** One bounded HUD card and short native, non-positional sound; no background workers. */
public final class ClipFeedback {
    private long lastSound;
    private static long lastClick;
    private static final SoundEvent BUTTON=SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("volt","button"));
    public static void click(net.minecraft.client.sounds.SoundManager manager){
        var v=VoltClient.INSTANCE;if(v==null||v.config.buttonVolume<=0)return;
        long now=System.nanoTime();if(now-lastClick<35_000_000L)return;lastClick=now;
        manager.play(SimpleSoundInstance.forUI(BUTTON,1f,v.config.buttonVolume/100f));
    }
    public void play(String key,int volume){
        String name=switch(key){case "volt.notice.saved"->"clip_saved";case "volt.notice.long_started"->"record_started";case "volt.notice.marked","volt.notice.clip_marked"->"marker";default->"";};
        long now=System.nanoTime();
        if(name.isEmpty()||volume<=0||now-lastSound<150_000_000L)return;
        lastSound=now;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
            SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("volt",name)),1f,Math.clamp(volume,0,100)/100f));
    }
    public void render(GuiGraphics g,VoltClient v,int screenWidth,int screenHeight,int hudY){
        int w=Math.min(296,screenWidth-16),h=48;
        boolean right=v.config.hudAnchor%2==1,bottom=v.config.hudAnchor>=2;
        int x=right?screenWidth-w-8:8,y=bottom?Math.max(8,hudY-h-8):hudY+26;
        double age=(System.currentTimeMillis()-v.noticeStarted)/1000.0;
        float shown=v.config.notificationAnimation?FeedbackMotion.visibility(age):1;
        x+=(right?1:-1)*Math.round((1-shown)*(w+12));
        VoltTheme.box(g,x,y,w,h,0xF2222428);
        boolean saved=v.notice.equals("volt.notice.saved");
        SmoothRaster.icon(g,saved?"check":"auto",x+12,y+13,22,VoltTheme.ACCENT);
        SmoothRaster.text(g,"VOLT",x+45,y+7,9,VoltTheme.MUTED,true);
        g.enableScissor(x+44,y,x+w-8,y+h);
        SmoothRaster.text(g,VoltTheme.text(v.notice).getString(),x+45,y+23,10,VoltTheme.WHITE,false);
        g.disableScissor();
        int remaining=Math.round((w-16)*Math.clamp(1-(float)(age/3.2),0,1));
        g.fill(x+8,y+h-3,x+8+remaining,y+h-2,VoltTheme.ACCENT);
    }
}
