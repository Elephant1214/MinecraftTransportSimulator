package mcinterface1165.mixin.client;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface RenderInfoInvokerMixin {
    @Invoker("setPos")
    void invokeSetPos(double x, double y, double z);
}
