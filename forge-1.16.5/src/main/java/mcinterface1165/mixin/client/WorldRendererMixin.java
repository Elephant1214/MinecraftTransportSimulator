package mcinterface1165.mixin.client;

import mcinterface1165.InterfaceRender;
import mcinterface1165.WrapperWorld;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.systems.ConfigSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow
    @Final
    private BufferBuilderStorage bufferBuilders;
    @Shadow
    @Final
    private MinecraftClient client;

    /**
     * Need this to render translucent things at the right time.  MC doesn't properly support this natively.
     * Instead, it tries to render translucent things with the regular things and fouls the depth buffer.
     */
    @Inject(method = "render", at = @At(value = "TAIL"))
    public void renderTranslucent(MatrixStack stack, float partialTicks, long finishTimeNano, boolean drawBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmap, Matrix4f projection, CallbackInfo ci) {
        VertexConsumerProvider.Immediate entityVertexConsumer = bufferBuilders.getEntityVertexConsumers();
        //Set camera offset point for later.
        Vec3d position = client.gameRenderer.getCamera().getPos();
        InterfaceRender.renderCameraOffset.set(position.x, position.y, position.z);
        if (ConfigSystem.settings.general.forceRenderLastSolid.value) {
            InterfaceRender.doRenderCall(stack, entityVertexConsumer, false, partialTicks);
        }
        InterfaceRender.doRenderCall(stack, entityVertexConsumer, true, partialTicks);
    }

    /**
     * This changes the heightmap of the rain checker to block rain from vehicles.
     * Better than trying to do block placement which has a host of issues.
     */
    @Redirect(method = "renderWeather", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getTopPosition(Lnet/minecraft/world/Heightmap$Type;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;"))
    public BlockPos blockRainInVehicles(World world, Heightmap.Type pHeightmapType, BlockPos pPos) {
        Point3D position = new Point3D(pPos.getX() + 0.5, world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pPos).getY(), pPos.getZ() + 0.5);
        WrapperWorld.getWrapperFor(world).adjustHeightForRain(position);
        return new BlockPos(pPos.getX(), Math.ceil(position.y), pPos.getZ());
    }
}
