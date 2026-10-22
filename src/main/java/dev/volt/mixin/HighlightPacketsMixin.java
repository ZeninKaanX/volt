package dev.volt.mixin;

import dev.volt.VoltClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class HighlightPacketsMixin {
    @Inject(method="handleDamageEvent",at=@At("TAIL"))
    private void volt$damage(ClientboundDamageEventPacket packet,CallbackInfo ci){if(VoltClient.INSTANCE!=null&&VoltClient.INSTANCE.highlights!=null)VoltClient.INSTANCE.highlights.damage(packet);}
    @Inject(method="handleEntityEvent",at=@At("TAIL"))
    private void volt$event(ClientboundEntityEventPacket packet,CallbackInfo ci){if(VoltClient.INSTANCE!=null&&VoltClient.INSTANCE.highlights!=null)VoltClient.INSTANCE.highlights.entityEvent(packet);}
    @Inject(method="handleUpdateAdvancementsPacket",at=@At("TAIL"))
    private void volt$advancement(ClientboundUpdateAdvancementsPacket packet,CallbackInfo ci){if(VoltClient.INSTANCE!=null&&VoltClient.INSTANCE.highlights!=null)VoltClient.INSTANCE.highlights.advancements(packet);}
}
