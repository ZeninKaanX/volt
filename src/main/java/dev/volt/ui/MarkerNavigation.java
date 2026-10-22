package dev.volt.ui;
import dev.volt.Clip;
import java.util.List;
public final class MarkerNavigation {
    private MarkerNavigation(){}
    public static double adjacent(List<Clip.Marker> markers,double position,boolean forward,double duration){
        return markers.stream().mapToDouble(Clip.Marker::second).filter(Double::isFinite).filter(x->x>=0&&x<=duration)
            .filter(x->forward?x>position+.05:x<position-.05).reduce(forward?duration:0,forward?Math::min:Math::max);
    }
}
