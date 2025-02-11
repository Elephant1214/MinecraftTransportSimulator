package mcinterface1165;

import minecrafttransportsimulator.baseclasses.Point3D;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Builder for an entity to forward rendering calls to all internal renderer.  This is due to prevent
 * MC from culling entities when it should be rendering them instead.  MC does this when you can't see
 * the chunk the entity is in, or if they are above the world, but we don't want that.
 *
 * @author don_bruce
 */
@EventBusSubscriber
public class BuilderEntityRenderForwarder extends ABuilderEntityBase {
    public static RegistryObject<EntityType<BuilderEntityRenderForwarder>> E_TYPE4;

    protected PlayerEntity playerFollowing;

    public BuilderEntityRenderForwarder(EntityType<? extends BuilderEntityRenderForwarder> eType, World world) {
        super(eType, world);
    }

    public BuilderEntityRenderForwarder(PlayerEntity playerFollowing) {
        this(E_TYPE4.get(), playerFollowing.world);
        this.playerFollowing = playerFollowing;
        Vec3d playerPos = playerFollowing.getPos();
        this.setPos(playerPos.x, playerPos.y, playerPos.z);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (this.playerFollowing != null && this.playerFollowing.world == this.world && this.playerFollowing.isAlive()) {
            //Need to move the fake entity forwards to account for the partial ticks interpolation MC does.
            //If we don't do this, and we move faster than 1 block per tick, we'll get flickering.
            WrapperPlayer playerWrapper = WrapperPlayer.getWrapperFor(this.playerFollowing);
            double playerVelocity = this.playerFollowing.getVelocity().length();
            Point3D playerEyesVec = playerWrapper.getLineOfSight(Math.max(1, playerVelocity / 2));
            Point3D playerEyeOffset = new Point3D(0, 0.5, 0).rotate(playerWrapper.getOrientation()).add(playerWrapper.getPosition()).add(playerEyesVec);
            setPos(playerEyeOffset.x, playerEyeOffset.y + (playerWrapper.getEyeHeight() + playerWrapper.getSeatOffset()) * playerWrapper.getVerticalScale(), playerEyeOffset.z);
        } else if (!this.world.isClient) {
            //Don't restore saved entities on the server.
            //These get loaded, but might not tick if they're out of chunk range.
            remove();
        } else if (!this.loadedFromSavedNBT && this.loadFromSavedNBT) {
            //Load player following from client NBT.
            this.playerFollowing = this.world.getPlayerByUuid(this.lastLoadedNBT.getUuid("playerFollowing"));
            this.loadedFromSavedNBT = true;
            this.lastLoadedNBT = null;
        }
    }

    @Override
    public boolean shouldRender(double pDistance) {
        //Need to render in pass 1 to render transparent things in the world like light beams.
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (this.playerFollowing != null) {
            //Player valid, save it and return the modified tag.
            tag.putUuid("playerFollowing", this.playerFollowing.getUuid());
        }
        return tag;
    }
}
