package dev.volt.compat;

import java.util.*;

/** Only release Volt defaults. Other mods and explicit user assignments remain untouched. */
public final class DefaultKeyPolicy {
    public record Binding(String id,String key,boolean defaultBinding,boolean debugChord) { }
    public static Set<String> conflicts(List<Binding> bindings){
        Set<String> occupied=new HashSet<>(),result=new HashSet<>();
        for(Binding b:bindings)if(!b.id().startsWith("key.volt.")&&!b.debugChord()&&!b.key().equals("key.keyboard.unknown"))occupied.add(b.key());
        for(Binding b:bindings)if(b.id().startsWith("key.volt.")&&b.defaultBinding()&&occupied.contains(b.key()))result.add(b.id());
        return result;
    }
    private DefaultKeyPolicy(){ }
}
