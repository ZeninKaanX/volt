package dev.volt.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.volt.VoltClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class MinecraftMixin {
    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void volt$captureBeforeSwap(CallbackInfo ci) { VoltClient.captureFrame(); }
}
