package dev.volt.ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.function.*;

/** Seeking commits on release, avoiding a decoder process for each mouse movement. */
public final class SeekBar extends AbstractWidget {
    private final DoubleSupplier position;
    private final DoubleConsumer seek;
    private final double duration;
    private double target;
    private boolean dragging;
    public SeekBar(int x,int y,int w,double duration,DoubleSupplier position,DoubleConsumer seek){super(x,y,w,20,VoltTheme.text("volt.studio.seek"));this.duration=Math.max(.01,duration);this.position=position;this.seek=seek;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int head=getX()+(int)(width*Math.clamp((dragging?target:position.getAsDouble())/duration,0,1));g.fill(getX(),getY()+9,getX()+width,getY()+12,VoltTheme.LINE);g.fill(getX(),getY()+9,head,getY()+12,VoltTheme.ACCENT);VoltTheme.box(g,head-4,getY()+5,8,11,VoltTheme.ACCENT);}
    @Override public void onClick(MouseButtonEvent e,boolean dbl){dragging=true;move(e.x());}
    @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){move(e.x());}
    private void move(double x){target=Math.clamp((x-getX())/width*duration,0,duration);}
    @Override public boolean mouseReleased(MouseButtonEvent e){boolean handled=super.mouseReleased(e);if(dragging&&e.button()==0){move(e.x());dragging=false;seek.accept(target);return true;}return handled;}
    @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
}
