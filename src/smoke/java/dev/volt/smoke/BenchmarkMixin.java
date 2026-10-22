package dev.volt.smoke.mixin;
import dev.volt.smoke.SmokeMetrics;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Window.class)
public class BenchmarkMixin {
    @Inject(method="updateDisplay", at=@At("TAIL"))
    private void volt$measure(CallbackInfo ci) { SmokeMetrics.frame(); }
}
