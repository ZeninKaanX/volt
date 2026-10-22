package dev.volt.recording;
import dev.volt.VoltConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CaptureProfileTest {
 @Test void preservesExplicitQualityWhenGamePriorityOff(){VoltConfig c=new VoltConfig();c.height=1080;c.fps=60;c.performanceMode=false;assertEquals(new CaptureProfile(1920,1080,60),CaptureProfile.resolve(c,1920,1080,true));}
 @Test void boundsHardwareAndCpuCaptureWithoutChangingSourceAspect(){VoltConfig c=new VoltConfig();c.height=1080;c.fps=60;assertEquals(new CaptureProfile(1280,720,30),CaptureProfile.resolve(c,1920,1080,true));assertEquals(new CaptureProfile(960,540,30),CaptureProfile.resolve(c,1920,1080,false));assertEquals(new CaptureProfile(640,360,30),CaptureProfile.resolve(c,640,360,true));}
}
