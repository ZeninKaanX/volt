package dev.volt.compat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.volt.compat.DefaultKeyPolicy.Binding;

class DefaultKeyPolicyTest {
    @Test void releasesOnlyVoltDefaultWhenAnotherModUsesF8(){
        assertEquals(Set.of("key.volt.clip"),DefaultKeyPolicy.conflicts(List.of(
            new Binding("key.volt.clip","key.keyboard.f8",true,false),
            new Binding("key.aac_observer.hud","key.keyboard.f8",true,false),
            new Binding("key.volt.record","key.keyboard.f9",true,false))));
    }
    @Test void preservesExplicitUserAssignmentsAndIgnoresF3Chords(){
        assertTrue(DefaultKeyPolicy.conflicts(List.of(
            new Binding("key.volt.clip","key.keyboard.f8",false,false),
            new Binding("key.other","key.keyboard.f8",true,false),
            new Binding("key.volt.menu","key.keyboard.v",true,false),
            new Binding("key.debug.dumpVersion","key.keyboard.v",true,true))).isEmpty());
    }
}
