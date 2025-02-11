package mcinterface1165;

import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.BoundingBoxHitResult;
import minecrafttransportsimulator.baseclasses.Damage;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.components.AEntityB_Existing;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import minecrafttransportsimulator.entities.components.AEntityF_Multipart;
import minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics;
import minecrafttransportsimulator.items.components.IItemEntityProvider.IItemEntityFactory;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.systems.ConfigSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder for the main entity classes for MTS.  This builder allows us to create a new entity
 * class that we can control that doesn't have the wonky systems the MC entities have, such
 * as no roll axis, a single hitbox, and tons of immutable objects that get thrown away every update.
 * Constructor simply takes in a world instance per default MC standards, but doesn't create the actual
 * {@link AEntityB_Existing} until later.  This is because we can't build our entity at the same time MC creates
 * this instance as we might not yet have NBT data.  Instead, we simply hold on to the class and construct
 * it whenever we get called to do so.
 *
 * @author don_bruce
 */
@EventBusSubscriber
public class BuilderEntityExisting extends ABuilderEntityBase {
    public static RegistryObject<EntityType<BuilderEntityExisting>> E_TYPE2;
    private EntityDimensions mutableDims = new EntityDimensions(1.0F, 1.0F, false);

    /**
     * Maps Entity class names to instances of the IItemEntityProvider class that creates them.
     **/
    protected static final Map<String, IItemEntityFactory> entityMap = new HashMap<>();

    /**
     * Current entity we are built around.  This MAY be null if we haven't loaded NBT from the server yet.
     **/
    protected AEntityB_Existing entity;
    /**
     * Last saved explosion position (used for damage calcs).
     **/
    private static Point3D lastExplosionPosition;
    /**
     * Collective for collision boxes.  These are used by this entity to make things collide with it.
     **/
    public WrapperAABBCollective collisionBoxes;
    /**
     * Collective for collision boxes.  These are used by this entity to make things interact and attack it.
     **/
    private WrapperAABBCollective interactAttackBoxes;

    public BuilderEntityExisting(EntityType<? extends BuilderEntityExisting> eType, World world) {
        super(eType, world);
    }

    @Override
    public void baseTick() {
        super.baseTick();

        //If our entity isn't null, update it and our position.
        if (this.entity != null) {
            //Check if we are still valid, or need to be set dead.
            if (!this.entity.isValid) {
                remove();
            } else {
                //Set the new position. 
                setPos(this.entity.position.x, this.entity.position.y, this.entity.position.z);

                //If we are outside valid bounds on the server, set us as dead and exit.
                //If we are outside height bounds, only remove if we are less than 0.  Don't remove for being too high.
                //If we are in height bounds, but outside spawnable bounds, remove, since we're over the world border.
                if (!this.world.isClient) {
                    BlockPos pos = getBlockPos();
                    if (World.isOutOfBuildLimitVertically(pos) ? getPos().y < 0 : !world.getWorldBorder().contains(pos)) {
                        remove();
                        return;
                    }
                }

                if (this.entity instanceof AEntityE_Interactable) {
                    AEntityE_Interactable<?> interactable = ((AEntityE_Interactable<?>) entity);

                    //Update AABBs.
                    //We need to know if we need to increase the max world collision bounds to detect this entity.
                    //Only do this after the first tick of the entity, as we might have some states that need updating
                    //on that first tick that would cause bad maths.
                    //We also do this only every second, as it prevents excess checks.
                    this.entity.world.beginProfiling("CollisionOverhead", true);
                    this.collisionBoxes = new WrapperAABBCollective(interactable, true);
                    this.interactAttackBoxes = new WrapperAABBCollective(interactable, false);
                    if (this.entity instanceof EntityVehicleF_Physics && interactable.ticksExisted > 1 && interactable.ticksExisted % 20 == 0) {
                        this.mutableDims = new EntityDimensions((float) Math.max(interactable.encompassingBox.widthRadius * 2F, interactable.encompassingBox.depthRadius * 2F), (float) interactable.encompassingBox.heightRadius * 2F, false);
                        //Make sure the collision bounds for MC are big enough to collide with this entity.
                        double maxEntityRadius = this.world.getMaxEntityRadius();
                        if (maxEntityRadius < interactable.encompassingBox.widthRadius || maxEntityRadius < interactable.encompassingBox.heightRadius || maxEntityRadius < interactable.encompassingBox.depthRadius) {
                            maxEntityRadius = Math.max(Math.max(interactable.encompassingBox.widthRadius, interactable.encompassingBox.depthRadius), interactable.encompassingBox.heightRadius);
                            if (maxEntityRadius < AEntityE_Interactable.MAX_ENTITY_RADIUS) {
                                this.world.increaseMaxEntityRadius(maxEntityRadius);
                            } else {
                                InterfaceManager.coreInterface.logError("Attempted to set the world entity max size to way too big of a number (" + maxEntityRadius + ") for " + entity + "  Removing entity from world as this is likely a pack error that will cause lots of TPS loss!");
                                remove();
                            }
                        }
                    }
                    this.entity.world.endProfiling();
                }
            }
        } else {
            //If we have NBT, and haven't loaded it, do so now.
            if (!this.loadedFromSavedNBT && this.loadFromSavedNBT) {
                WrapperWorld worldWrapper = WrapperWorld.getWrapperFor(this.world);
                try {
                    WrapperNBT data = new WrapperNBT(this.lastLoadedNBT);
                    this.entity = entityMap.get(this.lastLoadedNBT.getString("entityid")).restoreEntityFromData(worldWrapper, data);
                    this.entity.world.addEntity(this.entity);
                    if (this.entity instanceof AEntityF_Multipart) {
                        ((AEntityF_Multipart<?>) this.entity).addPartsPostAddition(null, data);
                    }
                    this.loadedFromSavedNBT = true;
                    this.lastLoadedNBT = null;
                } catch (Exception e) {
                    InterfaceManager.coreInterface.logError("Failed to load entity on builder from saved NBT.  Did a pack change?");
                    InterfaceManager.coreInterface.logError(e.getMessage());
                    remove();
                }
            }
        }
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return this.mutableDims;
    }

    @Override
    public void remove() {
        super.remove();
        //Notify internal entity of it being invalid.
        if (this.entity != null) {
            this.entity.remove();
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (ConfigSystem.settings.damage.allowExternalDamage.value && !this.world.isClient && this.entity instanceof AEntityF_Multipart) {
            AEntityF_Multipart<?> multipart = ((AEntityF_Multipart<?>) this.entity);
            if (multipart instanceof EntityVehicleF_Physics) {
                amount *= ConfigSystem.externalDamageOverrides.overrides.get(multipart.definition.packID).get(multipart.definition.systemName);
            }
            Entity attacker = source.getSource();
            Entity trueSource = source.getAttacker();
            WrapperPlayer playerSource = trueSource instanceof PlayerEntity ? WrapperPlayer.getWrapperFor((PlayerEntity) trueSource) : null;
            if (lastExplosionPosition != null && source.isExplosive()) {
                //We encountered an explosion.  These may or may not have have entities linked to them.  Depends on if
                //it's a player firing a gun that had a bullet, or a random TNT lighting in the world.
                //Explosions, unlike other damage sources, can hit multiple collision boxes on an entity at once.
                BoundingBox explosiveBounds = new BoundingBox(lastExplosionPosition, amount, amount, amount);
                for (BoundingBox box : this.interactAttackBoxes.getBoxes()) {
                    if (box.intersects(explosiveBounds)) {
                        multipart.attack(new Damage(amount, box, null, playerSource, null).setExplosive());
                    }
                }
                lastExplosionPosition = null;
            } else if (attacker != null) {
                //Check the damage at the current position of the attacker.
                Vec3d attackerMcPos = attacker.getPos();
                Point3D attackerPosition = new Point3D(attackerMcPos.x, attackerMcPos.y, attackerMcPos.z);
                for (BoundingBox box : this.interactAttackBoxes.getBoxes()) {
                    if (box.isPointInside(attackerPosition, null)) {
                        multipart.attack(new Damage(amount, box, null, playerSource, null));
                        return true;
                    }
                }

                //No damage from direct attack, see if we have movement and are a projectile.
                Vec3d mcMovement = attacker.getVelocity();
                if (mcMovement.lengthSquared() != 0) {
                    //Check the theoretical position of the entity should it have moved.
                    //Some projectiles may call their attacking code before updating their positions.
                    //We do raytracing here to catch this movement.
                    Point3D endPosition = attackerPosition.copy().add(mcMovement.x, mcMovement.y, mcMovement.z);
                    Collection<BoundingBoxHitResult> hitResults = multipart.getHitBoxes(attackerPosition, endPosition, new BoundingBox(attackerPosition, endPosition), false);
                    if (hitResults != null) {
                        multipart.attackProjectile(new Damage(amount, null, null, playerSource, null), null, hitResults);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public Box getBoundingBox() {
        //Override this to make collision checks work with the multiple collision points.
        //We return the collision boxes as a wrapper here as we need a bounding box large enough to encompass both.
        return this.collisionBoxes != null ? this.collisionBoxes : super.getBoundingBox();
    }

    @Override
    public boolean isCollidable() {
        //This need to return false as otherwise MC tries to just collide with the encompassing WrapperAABB.
        //This causes the player to constantly auto-jump.  We get around this by returning false here and overriding the collision
        //code via a mixin.  This forces MC to only collide with the sub-boxes.  1.12.2 let us split collision and interaction, but
        //this MC version only lets us use a single box for both and this confuses it since it doesn't support multi-collision entities.
        return false;
    }

    @Override
    public boolean collides() {
        //This need to return true.  Even though the returned collision boxes don't include interaction boxes like those for parts, projectiles
        //use this method with their raytracing to see if they can hit us.
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (this.entity != null) {
            //Entity is valid, save it and return the modified tag.
            //Also save the class ID so we know what to construct when MC loads this Entity back up.
            this.entity.save(new WrapperNBT(tag));
            tag.putString("entityid", this.entity.getClass().getSimpleName());
        }
        return tag;
    }

    /**
     * We need to use explosion events here as we don't know where explosions occur in the world.
     * This results in them being position-less, so we can't get the collision box they hit for damage.
     * Whenever we have an explosion detonated in the world, save it's position.  We can then use it
     * in {@link #damage(DamageSource, float)} to tell the system which part to attack.
     */
    @SubscribeEvent
    public static void onIVExplode(ExplosionEvent.Detonate event) {
        if (!event.getWorld().isClient) {
            lastExplosionPosition = new Point3D(event.getExplosion().getPosition().x, event.getExplosion().getPosition().y, event.getExplosion().getPosition().z);
        }
    }
}
