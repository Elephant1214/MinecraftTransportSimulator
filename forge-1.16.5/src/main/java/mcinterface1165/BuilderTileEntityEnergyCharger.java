package mcinterface1165;

import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import minecrafttransportsimulator.blocks.tileentities.components.ITileEntityEnergyCharger;
import minecrafttransportsimulator.systems.ConfigSystem;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.RegistryObject;
import org.jetbrains.annotations.NotNull;

/**
 * Builder for tile entities that transform MC energy into power for other entities.
 *
 * @author don_bruce
 */
public class BuilderTileEntityEnergyCharger extends BuilderTileEntity implements IEnergyStorage {
    private static final int MAX_BUFFER = 1000;
    protected static RegistryObject<BlockEntityType<BuilderTileEntityEnergyCharger>> TE_TYPE2;
    private ITileEntityEnergyCharger charger;
    private int buffer;

    public BuilderTileEntityEnergyCharger() {
        super(TE_TYPE2.get());
    }

    @Override
    protected void setTileEntity(ATileEntityBase<?> tile) {
        super.setTileEntity(tile);
        this.charger = (ITileEntityEnergyCharger) tile;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.world.isClient && this.charger != null) {
            //Try and charge the internal TE.
            if (this.buffer > 0) {
                double amountToCharge = this.charger.getChargeAmount();
                if (amountToCharge != 0) {
                    int amountToRemoveFromBuffer = (int) (amountToCharge / ConfigSystem.settings.general.rfToElectricityFactor.value);
                    if (amountToRemoveFromBuffer > this.buffer) {
                        amountToRemoveFromBuffer = this.buffer;
                        amountToCharge = amountToRemoveFromBuffer * ConfigSystem.settings.general.rfToElectricityFactor.value;
                    }
                    this.charger.chargeEnergy(amountToCharge);
                    this.buffer -= amountToRemoveFromBuffer;
                }
            }
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (this.buffer == MAX_BUFFER) {
            return 0;
        } else {
            int amountToStore = MAX_BUFFER - buffer;
            if (amountToStore > maxReceive) {
                amountToStore = maxReceive;
            }
            if (!simulate) {
                this.buffer += amountToStore;
            }
            return amountToStore;
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return this.buffer;
    }

    @Override
    public int getMaxEnergyStored() {
        return MAX_BUFFER;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, Direction facing) {
        if (capability == CapabilityEnergy.ENERGY && facing != null) {
            return LazyOptional.of(() -> (T) this);
        } else {
            return super.getCapability(capability, facing);
        }
    }
}
