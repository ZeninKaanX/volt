package dev.volt.highlights;

import dev.volt.*;
import dev.volt.recording.ReplayRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import java.util.*;

/** Adapts authoritative client packets to small deterministic highlight rules. */
public final class AutoHighlights {
    private final VoltClient v;
    public final HighlightRules rules;
    private final HighlightQueue queue;
    private ClientLevel scope;
    private final Map<Integer,Long> participation=new LinkedHashMap<>();
    public AutoHighlights(VoltClient v){this.v=v;queue=new HighlightQueue(v.config);rules=new HighlightRules(v.config,this::trigger);}
    private static long now(){return System.nanoTime()/1_000_000;}
    private void scope(ClientLevel level){
        if(scope==level)return;
        // Save the available part of a pending encounter before crossing worlds/dimensions.
        flush(true);queue.discard();v.recorder.releaseHighlight();rules.reset();participation.clear();scope=level;
    }
    public void attack(Entity target){
        Minecraft mc=Minecraft.getInstance();scope(mc.level);
        if(mc.player==null||mc.player.isSpectator()||!(target instanceof Player)||!v.config.enabled)return;
        rules.attack(target.getId(),now());
    }
    public void damage(ClientboundDamageEventPacket packet){
        Minecraft mc=Minecraft.getInstance();scope(mc.level);
        if(mc.level==null||mc.player==null||!v.config.autoHighlights)return;
        Entity target=mc.level.getEntity(packet.entityId());if(target==null)return;
        var source=packet.getSource(mc.level);long now=now();
        if(target==mc.player){rules.incomingDamage();return;}
        if(source.getEntity()!=mc.player){
            if(target instanceof Player&&source.getEntity() instanceof Player)participation.remove(target.getId());
            return;
        }
        if(target instanceof Player||boss(target)){
            participation.put(target.getId(),now);
            while(participation.size()>8)participation.remove(participation.keySet().iterator().next());
        }
        if(target instanceof Player&&source.getDirectEntity()==mc.player){
            int hits=rules.confirmedMelee(target.getId(),now);
            if(hits>v.config.comboHits)queue.comboProgress(hits,now);
        }
    }
    public void entityEvent(ClientboundEntityEventPacket packet){
        Minecraft mc=Minecraft.getInstance();scope(mc.level);
        if(mc.level==null||mc.player==null)return;
        Entity entity=packet.getEntity(mc.level);
        if(entity==mc.player&&packet.getEventId()==35)rules.totem(now());
        else if(entity!=null&&packet.getEventId()==3)defeated(entity,now());
    }
    public void advancements(ClientboundUpdateAdvancementsPacket packet){
        Minecraft mc=Minecraft.getInstance();scope(mc.level);if(mc.player==null)return;
        long now=now();packet.getProgress().forEach((id,progress)->rules.advancement(id.toString(),progress.isDone(),packet.shouldReset(),now));
    }
    private static boolean boss(Entity entity){return entity instanceof EnderDragon||entity instanceof WitherBoss;}
    private void defeated(Entity entity,long now){
        Long hit=participation.remove(entity.getId());if(hit==null)return;
        if(boss(entity)&&now-hit<=120000)rules.boss(now);
        else if(entity instanceof Player&&now-hit<=10000)rules.pvpKill(now);
    }
    private void trigger(HighlightRules.Trigger trigger){
        if(v.recorder.state!=ReplayRecorder.State.BUFFERING||v.recorder.originNanos==0)return;
        boolean wasEmpty=queue.pending()==null;
        if(queue.accept(trigger,trigger.time(),v.recorder.elapsed(),v.recorder.originNanos,v.context())){
            v.recorder.reserveHighlight(queue.pending().start);
            if(!trigger.reason().equals("death"))v.recorder.mark(trigger.reason());
            if(wasEmpty)v.notify("volt.notice.auto_highlight");
        }
    }
    public void tick(Minecraft mc){
        scope(mc.level);
        if(!v.config.autoHighlights||!v.config.enabled||mc.player==null||mc.level==null||v.recorder.state!=ReplayRecorder.State.BUFFERING){
            queue.discard();v.recorder.releaseHighlight();
            if(!v.config.autoHighlights||!v.config.enabled)rules.reset();
            return;
        }
        long now=now();
        if(!mc.player.isCreative()&&!mc.player.isSpectator()){
            rules.health(mc.player.getHealth(),now);
            rules.fall(mc.player.fallDistance,mc.player.onGround(),mc.player.isInWater(),mc.player.isFallFlying()||mc.player.getAbilities().flying,mc.player.getHealth(),now);
        }
        // Only the few entities that we actually damaged; never scan all world entities.
        if(!participation.isEmpty())for(Integer id:new ArrayList<>(participation.keySet())){
            Entity entity=mc.level.getEntity(id);Long hit=participation.get(id);
            if(entity==null||hit==null||now-hit>120000)participation.remove(id);
            else if(entity instanceof LivingEntity living&&living.isDeadOrDying())defeated(entity,now);
        }
        flush(false);
    }
    private void flush(boolean immediate){
        HighlightQueue.Pending pending=queue.pending();if(pending==null)return;
        long now=now();
        if(pending.session!=v.recorder.originNanos||!v.config.autoHighlights||!v.config.enabled){queue.discard();v.recorder.releaseHighlight();return;}
        if(!immediate&&now<pending.due)return;
        if(v.recorder.isExporting()){
            if(immediate||now-pending.firstTime>20000){queue.discard();v.recorder.releaseHighlight();}
            return; // Manual saves take precedence over automatic saves.
        }
        String label=Component.translatable("volt.highlight."+pending.reason,pending.comboHits).getString();
        if(v.recorder.saveHighlight(pending.start,v.recorder.elapsed(),pending.context,pending.reason,pending.comboHits,label)){
            queue.submitted(now);v.recorder.releaseHighlight();
        }else if(immediate){queue.discard();v.recorder.releaseHighlight();}
    }
    public boolean pending(){return queue.pending()!=null;}
    public void death(){rules.death(now());}
}
