package dev.volt.highlights;

import dev.volt.VoltConfig;
import dev.volt.recording.ReplayRecorder.Context;
import java.util.*;

/** Combines one encounter into one clip with 15s pre-roll and 5s post-roll. */
public final class HighlightQueue {
    public static final class Pending {
        public final long firstTime,session;
        public final double start;
        public final Context context;
        public long due;
        public String reason;
        public int priority,comboHits;
        Pending(HighlightRules.Trigger trigger,long now,double recorderTime,long session,Context context){
            firstTime=now;this.session=session;start=Math.max(0,recorderTime-15);this.context=context;
            due=now+5000;reason=trigger.reason();priority=trigger.priority();if(reason.equals("combo"))comboHits=trigger.value();
        }
    }
    private final VoltConfig config;
    private final Deque<Long> exported=new ArrayDeque<>();
    private Pending pending;
    public HighlightQueue(VoltConfig config){this.config=config;}
    public Pending pending(){return pending;}
    public boolean accept(HighlightRules.Trigger trigger,long now,double recorderTime,long session,Context context){
        if(!config.autoHighlights||!config.enabled)return false;
        if(pending==null){
            while(!exported.isEmpty()&&now-exported.peekFirst()>=60000)exported.removeFirst();
            if(exported.size()>=config.autoClipsPerMinute||!exported.isEmpty()&&now-exported.peekLast()<12000)return false;
            pending=new Pending(trigger,now,recorderTime,session,context);
        }else{
            if(pending.session!=session)return false;
            pending.due=Math.min(now+5000,pending.firstTime+12000);
            if(trigger.priority()>pending.priority){pending.reason=trigger.reason();pending.priority=trigger.priority();}
            if(trigger.reason().equals("combo"))pending.comboHits=Math.max(pending.comboHits,trigger.value());
        }
        return true;
    }
    public void comboProgress(int hits,long now){
        if(pending!=null&&pending.comboHits>0){pending.comboHits=Math.max(pending.comboHits,hits);pending.due=Math.min(now+5000,pending.firstTime+12000);}
    }
    public void submitted(long now){exported.addLast(now);pending=null;}
    public void discard(){pending=null;}
}
