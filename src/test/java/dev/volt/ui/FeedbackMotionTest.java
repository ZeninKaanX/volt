package dev.volt.ui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class FeedbackMotionTest {
    @Test void boundedAndAbsentOutsideLifetime(){
        assertEquals(0,FeedbackMotion.visibility(-1));assertEquals(0,FeedbackMotion.visibility(3.2));assertEquals(0,FeedbackMotion.visibility(Double.NaN));
        for(double t=0;t<3.3;t+=.001){float x=FeedbackMotion.visibility(t);assertTrue(x>=0&&x<=1);}
    }
    @Test void stableReadingTimeAndSmoothTransitions(){
        assertEquals(.5f,FeedbackMotion.visibility(.11),.001);assertEquals(1,FeedbackMotion.visibility(.22));assertEquals(1,FeedbackMotion.visibility(2.95));assertEquals(.5f,FeedbackMotion.visibility(3.075),.001);
    }
}
