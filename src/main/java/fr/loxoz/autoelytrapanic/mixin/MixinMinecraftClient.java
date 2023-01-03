package fr.loxoz.autoelytrapanic.mixin;

import fr.loxoz.autoelytrapanic.AutoElytraPanic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Shadow @Final private RenderTickCounter renderTickCounter;

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(boolean tick, CallbackInfo ci) {
        if (AutoElytraPanic.inst() != null) {
            AutoElytraPanic.inst().onRenderTick((MinecraftClient) (Object) this, renderTickCounter.tickDelta);
        }
    }
}
