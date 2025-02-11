package mcinterface1165.mixin.client;

import mcinterface1165.BuilderEntityExisting;
import mcinterface1165.BuilderEntityLinkedSeat;
import mcinterface1165.BuilderEntityRenderForwarder;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements ClientPlayPacketListener {
    @Shadow
    private ClientWorld world;

    /**
     * Need this to spawn our entity on the client.  MC doesn't handle new entities, and Forge didn't make any hooks.
     * The only way we could do this is by extending LivingEntity, but that's a lotta overhead we don't want.
     */
    @SuppressWarnings("deprecation")
    @Inject(method = "onEntitySpawn", at = @At(value = "TAIL"))
    public void inject_handleAddEntity(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        int typeID = Registry.ENTITY_TYPE.getRawId(packet.getEntityTypeId());
        EntityType<?> type = Registry.ENTITY_TYPE.get(typeID);
        if (type == BuilderEntityExisting.E_TYPE2.get() || type == BuilderEntityLinkedSeat.E_TYPE3.get() || type == BuilderEntityRenderForwarder.E_TYPE4.get()) {
            Entity entity = EntityType.createInstanceFromId(typeID, world);
            if (entity != null) {
                int i = packet.getId();
                double d0 = packet.getX();
                double d1 = packet.getY();
                double d2 = packet.getZ();

                entity.updateTrackedPosition(d0, d1, d2);
                entity.refreshPositionAfterTeleport(d0, d1, d2);
                entity.pitch = packet.getPitch() * 360 / 256.0F;
                entity.yaw = packet.getYaw() * 360 / 256.0F;
                entity.setEntityId(i);
                entity.setUuid(packet.getUuid());
                world.addEntity(i, entity);
            } else {
                InterfaceManager.coreInterface.logError("Custom MC-Spawn packet failed to find entity!");
            }
        }
    }
}
