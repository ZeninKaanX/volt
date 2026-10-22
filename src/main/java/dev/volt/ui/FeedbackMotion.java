package dev.volt.ui;

public final class FeedbackMotion {
    private FeedbackMotion(){}
    public static float visibility(double seconds){
        if(!Double.isFinite(seconds)||seconds<=0||seconds>=3.2)return 0;
        double t=seconds<.22?seconds/.22:seconds>2.95?(3.2-seconds)/.25:1;
        t=Math.clamp(t,0,1);return (float)(t*t*(3-2*t));
    }
}
