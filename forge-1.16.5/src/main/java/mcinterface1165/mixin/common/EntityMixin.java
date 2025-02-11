package mcinterface1165.mixin.common;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mcinterface1165.BuilderEntityExisting;
import mcinterface1165.BuilderEntityLinkedSeat;
import mcinterface1165.WrapperWorld;
import minecrafttransportsimulator.baseclasses.BoundingBox;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private Vec3d immersiveVehicles$velocity;

    /**
     * Need this to force eye position while in vehicles.
     * Otherwise, MC uses standard position, which will be wrong.
     */
    @Inject(method = "getCameraPosVec", at = @At(value = "HEAD"), cancellable = true)
    private void inject_getEyePosition(float tickDelta, CallbackInfoReturnable<Vec3d> ci) {
        Entity entity = (Entity) ((Object) this);
        Entity riding = entity.getVehicle();
        if (riding instanceof BuilderEntityLinkedSeat) {
            BuilderEntityLinkedSeat builder = (BuilderEntityLinkedSeat) riding;
            if(builder.entity != null) {
                ci.setReturnValue(new Vec3d(builder.entity.riderHeadPosition.x, builder.entity.riderHeadPosition.y, builder.entity.riderHeadPosition.z));
            }
        }
    }

    /**
     * Need this to force collision with vehicles.  First we get variables when function is called, then
     * we overwrite the collided boxes.
     */
    @Inject(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getEntityCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private void inject_collide(Vec3d movement, CallbackInfoReturnable<Vec3d> ci) {
        this.immersiveVehicles$velocity = movement;
    }

    @ModifyVariable(
            method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
            at = @At(value = "STORE", opcode = Opcodes.ASTORE),
            ordinal = 4
    )
    private Stream<VoxelShape> modifyCollideList(Stream<VoxelShape> existingCollisions) {
        Entity entity = (Entity) ((Object) this);
        Box pCollisionBox = entity.getBoundingBox().stretch(this.immersiveVehicles$velocity);
        List<Box> vehicleCollisions = null;
        for (BuilderEntityExisting builder : entity.world.getNonSpectatingEntities(BuilderEntityExisting.class, pCollisionBox)) {
            if (builder.collisionBoxes != null) {
                if (builder.collisionBoxes.intersects(pCollisionBox)) {
                    for (BoundingBox box : builder.collisionBoxes.getBoxes()) {
                        Box convertedBox = WrapperWorld.convert(box);
                        if (convertedBox.intersects(pCollisionBox)) {
                            if (vehicleCollisions == null) {
                                vehicleCollisions = new ArrayList<>();
                            }
                            vehicleCollisions.add(convertedBox);
                        }
                    }
                }
            }
        }
        if (vehicleCollisions != null) {
            return Stream.concat(vehicleCollisions.stream().map(VoxelShapes::cuboid), existingCollisions);
        } else {
            return existingCollisions;
        }
    }
}
