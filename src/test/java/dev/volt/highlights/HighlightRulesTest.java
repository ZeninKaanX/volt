package dev.volt.highlights;

import dev.volt.VoltConfig;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HighlightRulesTest {
    final VoltConfig config=new VoltConfig();
    final List<HighlightRules.Trigger> events=new ArrayList<>();
    final HighlightRules rules=new HighlightRules(config,events::add);
    int hit(int target,long time){rules.attack(target,time);return rules.confirmedMelee(target,time+20);}
    @Test void confirmedComboTriggersOnceAndTracksFurtherHits(){
        for(int i=0;i<6;i++)assertEquals(i+1,hit(1,1000+i*600));
        assertEquals(1,events.size());assertEquals("combo",events.getFirst().reason());assertEquals(4,events.getFirst().value());
    }
    @Test void missesUnattributedDamageExpiredAttemptsAndDuplicatePacketsDoNotCount(){
        for(int i=0;i<8;i++)rules.attack(1,1000+i*600);
        assertTrue(events.isEmpty());assertEquals(0,rules.confirmedMelee(2,6000));
        assertEquals(0,rules.confirmedMelee(1,8000));
        assertEquals(1,hit(1,9000));assertEquals(0,rules.confirmedMelee(1,9050));
        assertEquals(0,hit(1,9100));assertEquals(2,hit(1,9600));assertTrue(events.isEmpty());
    }
    @Test void damageTargetChangeAndLongGapsBreakTheChain(){
        hit(1,1000);hit(1,1600);rules.incomingDamage();assertEquals(1,hit(1,2200));
        assertEquals(1,hit(2,2800));assertEquals(1,hit(2,6000));assertTrue(events.isEmpty());
    }
    @Test void cooldownAndDisabledCategoriesSuppressSpam(){
        for(int round=0;round<2;round++){rules.incomingDamage();for(int i=0;i<4;i++)hit(1,1000+round*4000+i*600);}
        assertEquals(1,events.size());config.autoCombatClips=false;rules.pvpKill(10000);assertEquals(1,events.size());
        config.autoHighlights=false;rules.totem(11000);rules.boss(12000);assertEquals(1,events.size());
    }
    @Test void advancementLoginSyncCommonAndRepeatedEventsAreIgnored(){
        rules.advancement("minecraft:story/mine_diamond",true,true,1000);
        rules.advancement("minecraft:story/mine_diamond",true,false,2000);
        rules.advancement("minecraft:story/root",true,false,3000);assertTrue(events.isEmpty());
        rules.advancement("minecraft:end/elytra",true,false,4000);
        rules.advancement("minecraft:end/elytra",true,false,80000);assertEquals(1,events.size());
    }
    @Test void clutchWaitsForLandingHealthAndRejectsNormalFallDamage(){
        rules.fall(9,false,false,false,20,1000);rules.fall(0,true,false,false,20,1500);
        rules.fall(0,true,false,false,20,1800);assertTrue(events.isEmpty());
        rules.fall(0,true,false,false,19,1900);assertEquals("clutch",events.getFirst().reason());
        rules.fall(12,false,false,false,20,60000);rules.fall(0,true,false,false,20,60500);
        rules.fall(0,true,false,false,10,61000);assertEquals(1,events.size());
    }
    @Test void flyingOrDyingCancelsClutch(){
        rules.fall(10,false,false,false,20,1000);rules.fall(0,false,false,true,20,1400);
        rules.fall(0,true,false,false,20,2000);rules.fall(10,false,false,false,20,3000);
        rules.fall(0,true,false,false,0,3500);rules.fall(0,true,false,false,0,4000);assertTrue(events.isEmpty());
    }
    @Test void closeCallRequiresSurvivalAndRecoveryBeforeRearming(){
        rules.health(3,1000);rules.health(3,5999);assertTrue(events.isEmpty());
        rules.health(3,6000);rules.health(3,100000);assertEquals(1,events.size());
        rules.health(20,101000);rules.health(2,102000);rules.health(2,107000);assertEquals(2,events.size());
        rules.health(20,200000);rules.health(2,201000);rules.health(0,205000);rules.health(0,208000);assertEquals(2,events.size());
    }
}
