package dev.volt.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.Locale;

/** A real range selector: dragging either handle updates the exact trim fields. */
public final class TrimRange extends AbstractWidget {
    private final EditBox start, end;
    private final double duration;
    private boolean movingStart;
    public TrimRange(int x,int y,int width,double duration,EditBox start,EditBox end){
        this(x,y,width,26,duration,start,end);
    }
    public TrimRange(int x,int y,int width,int height,double duration,EditBox start,EditBox end){
        super(x,y,width,height,VoltTheme.text("volt.trim.range"));this.start=start;this.end=end;this.duration=Math.max(.1,duration);
    }
    private double value(EditBox box,double fallback){try{return Math.clamp(Double.parseDouble(box.getValue().replace(',','.')),0,duration);}catch(NumberFormatException e){return fallback;}}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
        int x=getX()+6,w=width-12,y=getY()+10;
        double from=value(start,0),to=value(end,duration);
        int a=x+(int)(w*from/duration),b=x+(int)(w*to/duration);
        if(height>26){
            g.fill(x,getY(),a,getY()+height,0xBB000000);g.fill(b,getY(),x+w,getY()+height,0xBB000000);
            g.fill(a,getY(),b,getY()+2,VoltTheme.ACCENT);g.fill(a,getY()+height-2,b,getY()+height,VoltTheme.ACCENT);
            VoltTheme.box(g,a-4,getY(),8,height,VoltTheme.ACCENT);VoltTheme.box(g,b-4,getY(),8,height,VoltTheme.ACCENT);return;
        }
        VoltTheme.box(g,x,y,w,6,VoltTheme.LINE);VoltTheme.box(g,a,y,Math.max(1,b-a),6,0xFF7B9A63);
        VoltTheme.box(g,a-4,y-6,8,18,VoltTheme.ACCENT);VoltTheme.box(g,b-4,y-6,8,18,VoltTheme.ACCENT);
    }
    @Override public void onClick(MouseButtonEvent event,boolean doubleClick){
        double at=seconds(event.x());movingStart=Math.abs(at-value(start,0))<Math.abs(at-value(end,duration));move(at);
    }
    @Override protected void onDrag(MouseButtonEvent event,double dx,double dy){move(seconds(event.x()));}
    private double seconds(double x){return Math.clamp((x-getX()-6)/(width-12)*duration,0,duration);}
    private void move(double second){
        if(movingStart)start.setValue(String.format(Locale.ROOT,"%.2f",Math.clamp(second,0,Math.max(0,value(end,duration)-.05))));
        else end.setValue(String.format(Locale.ROOT,"%.2f",Math.clamp(second,Math.min(duration,value(start,0)+.05),duration)));
    }
    @Override public boolean keyPressed(KeyEvent key){
        if(key.key()==258){movingStart=!movingStart;return false;}
        if(key.key()==263||key.key()==262){move((movingStart?value(start,0):value(end,duration))+(key.key()==263?-.1:.1));return true;}
        return super.keyPressed(key);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
}
