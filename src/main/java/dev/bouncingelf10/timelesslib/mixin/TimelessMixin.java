package dev.bouncingelf10.timelesslib.mixin;

import dev.bouncingelf10.timelesslib.TimelessLib;
import dev.bouncingelf10.timelesslib.TimelessLibClient;
import dev.bouncingelf10.timelesslib.api.clock.TimelessClock;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class TimelessMixin {
    @Inject(method = "runTick(Z)V", at = @At("TAIL"))
    private void onRunTick(boolean partialTick, CallbackInfo ci) {
        TimelessClock.update();
        TimelessLibClient.getClientAnimationManager().update();
    }
}

