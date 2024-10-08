package mcinterface1165;

import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import minecrafttransportsimulator.blocks.tileentities.components.ITileEntityEnergyCharger;
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
public class BuilderBlockEntityEnergyCharger extends BuilderBlockEntity implements IEnergyStorage {
    protected static RegistryObject<BlockEntityType<BuilderBlockEntityEnergyCharger>> TE_ENERGY_CHARGER;

    private ITileEntityEnergyCharger charger;
    private static final int MAX_BUFFER = 1000;
    private int buffer;

    public BuilderBlockEntityEnergyCharger() {
        super(TE_ENERGY_CHARGER.get());
    }

    @Override
    protected void setTileEntity(ATileEntityBase<?> tile) {
        super.setTileEntity(tile);
        this.charger = (ITileEntityEnergyCharger) tile;
    }

    @Override
    public void tick() {
        super.tick();
        if (!world.isClient && charger != null) {
            //Try and charge the internal TE.
            if (buffer > 0) {
                int amountToCharge = charger.getChargeAmount();
                if (amountToCharge != 0) {
                    if (amountToCharge > buffer) {
                        amountToCharge = buffer;
                    }
                    charger.chargeEnergy(amountToCharge);
                    buffer -= amountToCharge;
                }
            }
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (buffer == MAX_BUFFER) {
            return 0;
        } else {
            int amountToStore = MAX_BUFFER - buffer;
            if (amountToStore > maxReceive) {
                amountToStore = maxReceive;
            }
            if (!simulate) {
                buffer += amountToStore;
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
        return buffer;
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
