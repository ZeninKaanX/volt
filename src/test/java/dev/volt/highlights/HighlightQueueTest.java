package dev.volt.highlights;

import dev.volt.VoltConfig;
import dev.volt.recording.ReplayRecorder.Context;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HighlightQueueTest {
    final VoltConfig config=new VoltConfig();final HighlightQueue queue=new HighlightQueue(config);
    final Context context=new Context("Tester","World","minecraft:overworld");
    HighlightRules.Trigger event(String reason,int value,int priority,long now){return new HighlightRules.Trigger(reason,value,priority,now);}
    boolean accept(long now){return queue.accept(event("combo",4,50,now),now,30,1,context);}
    @Test void capturesBeforeAndAfterAndCombinesRelatedEvents(){
        assertTrue(accept(1000));assertEquals(15,queue.pending().start);assertEquals(6000,queue.pending().due);
        queue.comboProgress(7,3000);assertEquals(7,queue.pending().comboHits);assertEquals(8000,queue.pending().due);
        queue.accept(event("totem",1,90,4000),4000,33,1,context);
        assertEquals("totem",queue.pending().reason);assertEquals(7,queue.pending().comboHits);
        queue.comboProgress(9,15000);assertEquals(13000,queue.pending().due);assertEquals(context,queue.pending().context);
    }
    @Test void clampsShortSessionAndRejectsOtherSession(){
        assertTrue(queue.accept(event("totem",1,90,0),0,3,1,context));assertEquals(0,queue.pending().start);
        assertFalse(queue.accept(event("combo",4,50,1000),1000,4,2,context));assertEquals("totem",queue.pending().reason);
    }
    @Test void enforcesSpacingAndSlidingMinuteBudget(){
        assertTrue(accept(1000));queue.submitted(6000);assertFalse(accept(17999));
        assertTrue(accept(18000));queue.submitted(23000);assertTrue(accept(35000));queue.submitted(40000);
        assertFalse(accept(52000));assertTrue(accept(66000));
    }
    @Test void masterOffAndRecorderOffPreventPendingClips(){
        config.autoHighlights=false;assertFalse(accept(1000));config.autoHighlights=true;config.enabled=false;assertFalse(accept(2000));
        config.enabled=true;assertTrue(accept(3000));queue.discard();assertNull(queue.pending());
    }
}
