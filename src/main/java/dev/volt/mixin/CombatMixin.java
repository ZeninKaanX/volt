package dev.volt.mixin;

import dev.volt.VoltClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class CombatMixin {
    @Inject(method="attack",at=@At("HEAD"))
    private void volt$attack(Player player,Entity target,CallbackInfo ci){
        if(VoltClient.INSTANCE!=null&&VoltClient.INSTANCE.highlights!=null)VoltClient.INSTANCE.highlights.attack(target);
    }
}
