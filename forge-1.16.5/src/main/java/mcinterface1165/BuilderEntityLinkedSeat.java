package mcinterface1165;

import minecrafttransportsimulator.entities.components.AEntityB_Existing;
import minecrafttransportsimulator.entities.instances.PartSeat;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.RegistryObject;

import java.util.List;
import java.util.UUID;

/**
 * Builder for an entity to sit in so they can ride another entity.  We use this rather
 * than a direct linking as entities with riders are removed by MC when the rider logs out.
 * This means that if we assigned this to the main entity, it would be removed when the rider left the server.
 * This is not ideal for things like trains where the engineer leaves and the main locomotive goes poof.
 *
 * @author don_bruce
 */
public class BuilderEntityLinkedSeat extends ABuilderEntityBase {
    public static RegistryObject<EntityType<BuilderEntityLinkedSeat>> E_TYPE3;

    /**
     * UUID of entity we are a seat on.  This MAY be null if we haven't loaded NBT from the server yet.
     **/
    private UUID entityUuid;
    /**
     * Current entity we are a seat on.  This MAY be null if we haven't loaded NBT from the server yet.
     **/
    public AEntityB_Existing entity;
    /**
     * Current rider for this seat.  This MAY be null if we haven't loaded NBT from the server yet.
     **/
    protected WrapperEntity rider;
    /**
     * Set to true when the rider dismounts.  We set their position the next tick to override it.
     **/
    private boolean dismountedRider;

    private int ticksWithoutRider;

    public BuilderEntityLinkedSeat(EntityType<? extends BuilderEntityLinkedSeat> entityType, World world) {
        super(BuilderEntityLinkedSeat.E_TYPE3.get(), world);
    }

    @Override
    public void baseTick() {
        super.baseTick();

        //If our entity isn't null, update us to the entity position.
        //What really matters is the player's position, and that comes later.
        //This just gets us "close enough" so we don't de-spawn or something.
        if (this.entity != null) {
            //Check if the entity we are a seat on is still valid, or need to be set dead.
            if (!this.entity.isValid) {
                remove();
            } else {
                setPos(this.entity.position.x, this.entity.position.y, this.entity.position.z);

                //Constantly check for the rider.  They might take a bit to load in.
                //If the rider dismounted us, just die.
                List<Entity> riders = getPassengerList();
                if (this.rider == null && !riders.isEmpty()) {
                    if (this.entity.rider != null) {
                        this.rider = (WrapperEntity) this.entity.rider;
                    } else {
                        this.rider = WrapperEntity.getWrapperFor(riders.get(0));
                        this.entity.setRider(this.rider, true);
                    }
                } else if (this.dismountedRider) {
                    //Need to delay this by a few ticks on the client, since this is seen on clients before servers and this
                    //will cause packet de-sync errors in the logs if we use this all the time.
                    if (!this.world.isClient || ++this.ticksWithoutRider == 10) {
                        remove();
                    }
                } else if (this.rider != null && riders.isEmpty()) {
                    if (!this.world.isClient || ++this.ticksWithoutRider == 10) {
                        remove();
                    }
                }
            }
        } else if (entityUuid != null) {
            if (this.age < 100) {
                WrapperWorld worldWrapper = WrapperWorld.getWrapperFor(this.world);
                this.entity = worldWrapper.getEntity(this.entityUuid);
            } else {
                InterfaceManager.coreInterface.logError("Found a seat but no entity was found for it.  Did a pack change?");
                remove();
            }
        } else if (this.loadFromSavedNBT) {
            if (this.lastLoadedNBT.contains("entityUUID")) {
                this.entityUuid = this.lastLoadedNBT.getUuid("entityUUID");
                this.loadedFromSavedNBT = true;
            } else {
                InterfaceManager.coreInterface.logError("Found a seat not linked to an entity?  The heck?");
                remove();
            }
        }
    }

    @Override
    public void remove() {
        super.remove();
        //Notify internal entity of rider being removed.
        if (this.entity != null && this.rider != null) {
            if (!this.world.isClient && this.rider.equals(this.entity.rider)) {
                this.entity.removeRider();
            }
            this.rider = null;
            this.entity = null;
        }
    }

    @Override
    public void updatePassengerPosition(Entity passenger) {
        //Forward passenger updates to the entity.
        //Need to verify the entity has a rider, it might not if we are on the
        //client and waiting for the rider packet.  Or on the server and waiting for loading of the player.
        if (this.entity != null && this.entity.rider != null) {
            this.entity.updateRider();
            //Call getters so it resets to current value.
            //This allows the calling of the method in other areas to see MC deltas.
            //Make sure the rider wasn't removed, however.
            if (this.entity.rider != null) {
                this.entity.rider.getYawDelta();
                this.entity.rider.getPitchDelta();
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        //Need to check if we have ticked.  MC, on loading this entity, first dismounts all riders.
        //This will cause IV to see a dismount when in actuality it's a loading sequence.
        if (this.age > 0) {
            this.dismountedRider = true;
        }
    }

    @Override
    public boolean shouldRiderSit() {
        return this.entity instanceof PartSeat ? !((PartSeat) this.entity).definition.seat.standing : super.shouldRiderSit();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (this.entity != null) {
            //Entity is valid, save UUID and return the modified tag.
            tag.putUuid("entityUUID", this.entity.uniqueUUID);
        }
        return tag;
    }
}
