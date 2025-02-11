package mcinterface1165;

import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.Damage;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.baseclasses.RotationMatrix;
import minecrafttransportsimulator.entities.components.AEntityB_Existing;
import minecrafttransportsimulator.entities.instances.PartSeat;
import minecrafttransportsimulator.jsondefs.JSONPotionEffect;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.IWrapperEntity;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.systems.ConfigSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.EntityDamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.LeadItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potion;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber
public class WrapperEntity implements IWrapperEntity {
    private static final Map<Entity, WrapperEntity> entityClientWrappers = new HashMap<>();
    private static final Map<Entity, WrapperEntity> entityServerWrappers = new HashMap<>();

    protected final Entity entity;
    private final Point3D mutablePosition = new Point3D();
    private final Point3D mutableVelocity = new Point3D();
    private final RotationMatrix mutableOrientation = new RotationMatrix();
    private final Point3D mutableSight = new Point3D();
    private final BoundingBox mutableBounds = new BoundingBox(new Point3D(), 0, 0, 0);
    private AEntityB_Existing cachedEntityRiding;
    private float lastPitchChecked;
    private float lastYawChecked;
    private float lastYawApplied;
    private float lastPitch;
    private float lastYaw;

    protected WrapperEntity(Entity entity) {
        this.entity = entity;
    }

    /**
     * Returns a wrapper instance for the passed-in entity instance.
     * Null may be passed-in safely to ease function-forwarding.
     * Wrapper is cached to avoid re-creating the wrapper each time it is requested.
     * If the entity is a player, then a player wrapper is returned.
     */
    public static WrapperEntity getWrapperFor(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return WrapperPlayer.getWrapperFor((PlayerEntity) entity);
        } else if (entity != null) {
            Map<Entity, WrapperEntity> entityWrappers = entity.world.isClient ? entityClientWrappers : entityServerWrappers;
            WrapperEntity wrapper = entityWrappers.get(entity);
            if (wrapper == null || !wrapper.isValid() || entity != wrapper.entity) {
                wrapper = new WrapperEntity(entity);
                entityWrappers.put(entity, wrapper);
            }
            return wrapper;
        } else {
            return null;
        }
    }

    /**
     * Remove all entities from our maps if we unload the world.  This will cause duplicates if we don't.
     */
    @SubscribeEvent
    public static void onIVWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isClient()) {
            entityClientWrappers.keySet().removeIf(entity1 -> event.getWorld() == entity1.world);
        } else {
            entityServerWrappers.keySet().removeIf(entity1 -> event.getWorld() == entity1.world);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return this.entity.equals(obj instanceof WrapperEntity ? ((WrapperEntity) obj).entity : obj);
    }

    @Override
    public int hashCode() {
        return this.entity.hashCode();
    }

    @Override
    public boolean isValid() {
        return this.entity != null && this.entity.isAlive();
    }

    @Override
    public UUID getID() {
        return this.entity.getUuid();
    }

    @Override
    public String getName() {
        return this.entity.getName().getString();
    }

    @Override
    public AWrapperWorld getWorld() {
        return WrapperWorld.getWrapperFor(this.entity.world);
    }

    @Override
    public AEntityB_Existing getEntityRiding() {
        if (cachedEntityRiding != null) {
            return cachedEntityRiding;
        } else {
            Entity mcEntityRiding = entity.getVehicle();
            if (mcEntityRiding instanceof BuilderEntityLinkedSeat) {
                AEntityB_Existing entityRiding = ((BuilderEntityLinkedSeat) mcEntityRiding).entity;
                //Need to check this as MC might have us as a rider on the builer, but we might not be a rider on the entity.
                if (entityRiding != null && this.equals(entityRiding.rider)) {
                    return entityRiding;
                }
            }
            return null;
        }
    }

    @Override
    public void setRiding(AEntityB_Existing entityToRide) {
        if (entityToRide != null) {
            //Don't re-add a seat entity if we are just changing seats.
            //This just causes extra execution logic.
            AEntityB_Existing entityRiding = getEntityRiding();
            if (entityRiding == null) {
                //Only spawn and start riding on the server, clients will get packets.
                if (!this.entity.world.isClient) {
                    BuilderEntityLinkedSeat seat = new BuilderEntityLinkedSeat(BuilderEntityLinkedSeat.E_TYPE3.get(), ((WrapperWorld) entityToRide.world).world);
                    seat.loadedFromSavedNBT = true;
                    seat.setPos(entityToRide.position.x, entityToRide.position.y, entityToRide.position.z);
                    seat.entity = entityToRide;
                    this.entity.world.spawnEntity(seat);
                    this.entity.startRiding(seat, true);
                }
            } else {
                //Just change entity reference, we will already be a rider on the entity at this point.
                ((BuilderEntityLinkedSeat) entity.getVehicle()).entity = entityToRide;
            }
            cachedEntityRiding = entityToRide;
        } else {
            entity.stopRiding();
            cachedEntityRiding = null;
        }
    }

    @Override
    public double getVerticalScale() {
        AEntityB_Existing riding = getEntityRiding();
        if (riding instanceof PartSeat) {
            PartSeat seat = (PartSeat) riding;
            if (seat.placementDefinition.playerScale != null) {
                if (seat.definition.seat.playerScale != null) {
                    return seat.scale.y * seat.placementDefinition.playerScale.y * seat.definition.seat.playerScale.y;
                } else {
                    return seat.scale.y * seat.placementDefinition.playerScale.y;
                }
            } else if (seat.definition.seat.playerScale != null) {
                return seat.scale.y * seat.definition.seat.playerScale.y;
            } else {
                return seat.scale.y;
            }
        }
        return 1.0;
    }

    @Override
    public double getSeatOffset() {
        //Vanilla entities (boat/minecart) normally have a 0.14 pixel delta from their base to where the entity sits.
        //We account for this here.
        AEntityB_Existing riding = getEntityRiding();
        if (riding instanceof PartSeat && !((PartSeat) riding).definition.seat.standing) {
            if (this.entity instanceof AnimalEntity) {
                //Animals are moved up 0.14 pixels (~2.25), for their sitting positions.  Un-do this.
                return this.entity.getHeightOffset() - 0.14D;
            } else if (this.entity instanceof VillagerEntity) {
                //Villagers get the same offset as players.
                return (-12D / 16D) * (30D / 32D);
            } else {
                Identifier registration = this.entity.getType().getRegistryName();
                if (registration != null && registration.getNamespace().equals("customnpcs")) {
                    //CNPCs seem to be offset by 3, but invert their model scaling for their sitting position.
                    return -3D / 16D * (32D / 30D);
                } else {
                    return this.entity.getHeightOffset();
                }
            }
        }
        return 0;
    }

    @Override
    public double getEyeHeight() {
        return this.entity.getStandingEyeHeight();
    }

    @Override
    public Point3D getPosition() {
        this.mutablePosition.set(this.entity.getX(), this.entity.getY(), this.entity.getZ());
        return this.mutablePosition;
    }

    @Override
    public Point3D getEyePosition() {
        AEntityB_Existing riding = getEntityRiding();
        return riding != null ? riding.riderEyePosition : getPosition().add(0, getEyeHeight() + getSeatOffset(), 0);
    }

    @Override
    public Point3D getHeadPosition() {
        AEntityB_Existing riding = getEntityRiding();
        return riding != null ? riding.riderHeadPosition : getPosition().add(0, getEyeHeight() + getSeatOffset(), 0);
    }

    @Override
    public void setPosition(Point3D position, boolean onGround) {
        if (this.cachedEntityRiding != null) {
            //Need to offset down to make bounding hitbox go down like normal. 
            this.entity.setPos(position.x, position.y + getSeatOffset(), position.z);
        } else {
            this.entity.setPos(position.x, position.y, position.z);
        }
        //Set fallDistance to 0 to prevent damage.
        this.entity.fallDistance = 0;
        this.entity.setOnGround(onGround);
    }

    @Override
    public void applyMotion(Point3D motion) {
        this.entity.addVelocity(motion.x, motion.y, motion.z);
        this.entity.velocityModified = true;
    }

    @Override
    public Point3D getVelocity() {
        //Need to manually put 0 here for Y since entities on ground have a constant -Y motion.
        this.mutableVelocity.set(this.entity.getVelocity().x, this.entity.isOnGround() ? 0 : this.entity.getVelocity().y, this.entity.getVelocity().z);
        return this.mutableVelocity;
    }

    @Override
    public void setVelocity(Point3D motion) {
        this.entity.setVelocity(motion.x, motion.y, motion.z);
    }

    @Override
    public RotationMatrix getOrientation() {
        if (this.lastPitchChecked != this.entity.pitch || this.lastYawChecked != this.entity.yaw) {
            this.lastPitchChecked = this.entity.pitch;
            this.lastYawChecked = this.entity.yaw;
            this.mutableOrientation.angles.set(this.entity.pitch, -this.entity.yaw, 0);
            this.mutableOrientation.setToAngles(this.mutableOrientation.angles);
        }
        return this.mutableOrientation;
    }

    @Override
    public void setOrientation(RotationMatrix rotation) {
        if (this.entity.world.isClient) {
            //Client-side expects the yaw keep going and not reset at the 360 bounds like our matrix does.
            //Therefore, we need to check our delta from our rotation matrix and apply that VS the raw value.
            //Clamp delta to +/- 180 to ensure that we don't go 360 backwards when crossing the 0/360 zone.
            float yawDelta = ((float) -rotation.angles.y - this.lastYawApplied) % 360;
            if (yawDelta > 180) {
                yawDelta -= 360;
            } else if (yawDelta < -180) {
                yawDelta -= 360;
            }
            this.entity.yaw = this.lastYawApplied + yawDelta;
            this.lastYawApplied = this.entity.yaw;
        } else {
            this.entity.yaw = (float) -rotation.angles.y;
        }
        this.entity.pitch = (float) rotation.angles.x;
    }

    @Override
    public float getPitch() {
        return this.entity.pitch;
    }

    @Override
    public void setPitch(double pitch) {
        this.entity.pitch = (float) pitch;
    }

    @Override
    public float getPitchDelta() {
        float value = this.entity.pitch - this.lastPitch;
        this.lastPitch = this.entity.pitch;
        return value;
    }

    @Override
    public float getYaw() {
        return -this.entity.yaw;
    }

    @Override
    public void setYaw(double yaw) {
        this.entity.yaw = (float) -yaw;
    }

    @Override
    public float getYawDelta() {
        float value = this.entity.yaw - this.lastYaw;
        this.lastYaw = this.entity.yaw;
        return -value;
    }

    @Override
    public float getBodyYaw() {
        return this.entity instanceof LivingEntity ? -((LivingEntity) this.entity).bodyYaw : 0;
    }

    @Override
    public void setBodyYaw(double yaw) {
        if (this.entity instanceof LivingEntity) {
            this.entity.setBodyYaw((float) -yaw);
        }
    }

    @Override
    public Point3D getLineOfSight(double distance) {
        mutableSight.set(0, 0, distance).rotate(getOrientation());
        return mutableSight;
    }

    @Override
    public BoundingBox getBounds() {
        this.mutableBounds.widthRadius = this.entity.getWidth() / 2F;
        this.mutableBounds.heightRadius = this.entity.getHeight() / 2F;
        this.mutableBounds.depthRadius = this.entity.getWidth() / 2F;
        this.mutableBounds.globalCenter.set(this.entity.getX(), this.entity.getY() + this.mutableBounds.heightRadius, this.entity.getZ());
        return mutableBounds;
    }

    @Override
    public IWrapperNBT getData() {
        NbtCompound tag = new NbtCompound();
        this.entity.writeNbt(tag);
        return new WrapperNBT(tag);
    }

    @Override
    public void setData(IWrapperNBT data) {
        this.entity.readNbt(((WrapperNBT) data).tag);
    }

    @Override
    public boolean leashTo(IWrapperPlayer player) {
        PlayerEntity mcPlayer = ((WrapperPlayer) player).player;
        if (this.entity instanceof MobEntity) {
            ItemStack heldStack = mcPlayer.getMainHandStack();
            if (((MobEntity) this.entity).canBeLeashedBy(mcPlayer) && heldStack.getItem() instanceof LeadItem) {
                ((MobEntity) this.entity).attachLeash(mcPlayer, true);
                if (!mcPlayer.isCreative()) {
                    heldStack.decrement(1);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void attack(Damage damage) {
        if (damage.language == null) {
            throw new IllegalArgumentException("ERROR: Cannot attack an entity with a damage of no type and language component!");
        }
        DamageSource newSource = new EntityDamageSource(damage.language.getCurrentValue(), damage.entityResponsible != null ? ((WrapperEntity) damage.entityResponsible).entity : null) {
            @Override
            public Text getDeathMessage(LivingEntity player) {
                if (damage.entityResponsible != null) {
                    return new LiteralText(String.format(damage.language.getCurrentValue(), player.getDisplayName().getString(), ((WrapperEntity) damage.entityResponsible).entity.getDisplayName().getString()));
                } else {
                    return new LiteralText(String.format(damage.language.getCurrentValue(), player.getDisplayName().getString()));
                }
            }
        };
        if (damage.isFire) {
            newSource.setFire();
            this.entity.setFireTicks(5);
        }
        if (damage.knockback != null) {
            applyMotion(damage.knockback);
        }
        if (damage.isWater) {
            this.entity.extinguish();
            //Don't attack this entity with water.
            return;
        }
        if (damage.isExplosion) {
            newSource.setExplosive();
        }
        if (damage.ignoreArmor) {
            newSource.setBypassesArmor();
        }
        if (damage.ignoreCooldown && this.entity instanceof LivingEntity) {
            this.entity.timeUntilRegen = 0;
        }
        if (ConfigSystem.settings.damage.creativePlayerDamage.value) {
            newSource.setOutOfWorld();
        }
        this.entity.damage(newSource, (float) damage.amount);

        if (damage.effects != null) {
            damage.effects.forEach(this::addPotionEffect);
        }
    }

    @Override
    public void addPotionEffect(JSONPotionEffect effect) {
        if ((this.entity instanceof LivingEntity)) {
            Potion potion = Potion.byId(effect.name);
            if (potion != null) {
                potion.getEffects().forEach(mcEffect -> ((LivingEntity) this.entity).addStatusEffect(new StatusEffectInstance(mcEffect.getEffectType(), effect.duration, effect.amplifier, false, false)));
            } else {
                throw new NullPointerException("Potion " + effect.name + " does not exist.");
            }
        }
    }

    @Override
    public void removePotionEffect(JSONPotionEffect effect) {
        if ((entity instanceof LivingEntity)) {
            Potion potion = Potion.byId(effect.name);
            if (potion != null) {
                potion.getEffects().forEach(mcEffect -> ((LivingEntity) entity).removeStatusEffect(mcEffect.getEffectType()));
            } else {
                throw new NullPointerException("Potion " + effect.name + " does not exist.");
            }
        }
    }
}