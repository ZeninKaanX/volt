package dev.volt.highlights;

import dev.volt.VoltConfig;
import java.util.*;
import java.util.function.Consumer;

/** Deterministic, bounded event policy. No entity scans, disk access or per-frame work. */
public final class HighlightRules {
    public record Trigger(String reason,int value,int priority,long time) { }
    private static final Set<String> IMPORTANT_ADVANCEMENTS=Set.of(
        "minecraft:story/mine_diamond","minecraft:story/enter_the_nether","minecraft:story/enter_the_end",
        "minecraft:end/kill_dragon","minecraft:end/dragon_egg","minecraft:end/elytra",
        "minecraft:nether/obtain_ancient_debris","minecraft:nether/summon_wither","minecraft:nether/create_beacon",
        "minecraft:adventure/kill_all_mobs","minecraft:adventure/hero_of_the_village","minecraft:adventure/adventuring_time",
        "minecraft:adventure/sniper_duel","minecraft:adventure/totem_of_undying");
    private final VoltConfig config;
    private final Consumer<Trigger> output;
    private final Map<Integer,Long> attempts=new LinkedHashMap<>();
    private final Map<String,Long> cooldowns=new HashMap<>();
    private final Set<String> completed=new LinkedHashSet<>();
    private int target=-1,hits;
    private long firstHit,lastHit,lastConfirmation=Long.MIN_VALUE,criticalSince=-1,landedAt=-1;
    private boolean comboTriggered,criticalTriggered,fallArmed;
    private float fallHealth;
    public HighlightRules(VoltConfig config,Consumer<Trigger> output){this.config=config;this.output=output;}
    public void attack(int targetId,long now){
        if(!config.autoHighlights||!config.autoComboClip)return;
        if(target!=-1&&target!=targetId)resetCombo();
        attempts.put(targetId,now);
        while(attempts.size()>8)attempts.remove(attempts.keySet().iterator().next());
    }
    /** Counts only an attributed server damage event matching a recent actual attack attempt. */
    public int confirmedMelee(int targetId,long now){
        Long attempted=attempts.remove(targetId);
        if(attempted==null||now<attempted||now-attempted>1500)return 0;
        if(lastConfirmation!=Long.MIN_VALUE&&now-lastConfirmation<250)return 0;
        lastConfirmation=now;
        if(target!=targetId||now-lastHit>1800||now-firstHit>10000){target=targetId;hits=0;firstHit=now;comboTriggered=false;}
        lastHit=now;hits++;
        if(hits>=config.comboHits&&!comboTriggered){comboTriggered=true;emit("combo",hits,50,now,25000,config.autoComboClip);}
        return hits;
    }
    public void incomingDamage(){resetCombo();attempts.clear();}
    private void resetCombo(){target=-1;hits=0;firstHit=lastHit=0;comboTriggered=false;}
    public void pvpKill(long now){emit("pvp_kill",1,60,now,25000,config.autoCombatClips);}
    public void totem(long now){emit("totem",1,90,now,45000,config.autoAdventureClips||config.autoCombatClips);}
    public void boss(long now){emit("boss",1,100,now,60000,config.autoAdventureClips);}
    public void death(long now){emit("death",1,30,now,45000,config.autoDeathClip);}
    public void advancement(String id,boolean done,boolean initialSync,long now){
        if(!IMPORTANT_ADVANCEMENTS.contains(id))return;
        if(!done){completed.remove(id);return;}
        boolean fresh=completed.add(id);
        while(completed.size()>128)completed.remove(completed.iterator().next());
        if(!initialSync&&fresh&&IMPORTANT_ADVANCEMENTS.contains(id))emit("advancement",1,80,now,45000,config.autoAdventureClips);
    }
    public void health(float health,long now){
        if(health<=0){criticalSince=-1;criticalTriggered=false;resetCombo();return;}
        if(health>8){criticalSince=-1;criticalTriggered=false;}
        if(health<=4&&criticalSince<0)criticalSince=now;
        if(criticalSince>=0&&!criticalTriggered&&now-criticalSince>=5000){criticalTriggered=true;emit("close_call",1,65,now,90000,config.autoAdventureClips);}
    }
    public void fall(double distance,boolean onGround,boolean inWater,boolean flying,float health,long now){
        if(flying||health<=0){fallArmed=false;landedAt=-1;return;}
        if(!fallArmed&&distance>=8){fallArmed=true;fallHealth=health;landedAt=-1;}
        if(!fallArmed)return;
        if((onGround||inWater)&&landedAt<0)landedAt=now;
        if(landedAt>=0&&now-landedAt>=400){
            if(health>=fallHealth-2)emit("clutch",1,75,now,45000,config.autoAdventureClips);
            fallArmed=false;landedAt=-1;
        }
    }
    private void emit(String reason,int value,int priority,long now,long wait,boolean enabled){
        if(!config.autoHighlights||!enabled)return;
        Long previous=cooldowns.get(reason);if(previous!=null&&now-previous<wait)return;
        cooldowns.put(reason,now);output.accept(new Trigger(reason,value,priority,now));
    }
    public void reset(){attempts.clear();cooldowns.clear();completed.clear();resetCombo();lastConfirmation=Long.MIN_VALUE;criticalSince=landedAt=-1;criticalTriggered=fallArmed=false;}
}
