package dev.volt.recording;
import dev.volt.VoltConfig;
/** Explicit game-first ceilings; requested settings are retained for quality mode. */
public record CaptureProfile(int width,int height,int fps){
 public static CaptureProfile resolve(VoltConfig c,int sourceWidth,int sourceHeight,boolean hardware){
  int ceiling=c.performanceMode?Math.min(c.height,hardware?720:540):c.height;
  int h=Math.max(2,Math.min(ceiling,sourceHeight)/2*2);
  int w=Math.max(2,(int)((double)sourceWidth/Math.max(1,sourceHeight)*h)/2*2);
  return new CaptureProfile(w,h,c.performanceMode?Math.min(c.fps,30):c.fps);
 }
}
