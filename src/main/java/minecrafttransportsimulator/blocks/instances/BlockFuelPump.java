package minecrafttransportsimulator.blocks.instances;

import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.blocks.components.ABlockBaseDecor;
import minecrafttransportsimulator.blocks.components.IBlockFluidTankProvider;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityFuelPump;
import minecrafttransportsimulator.mcinterface.WrapperNBT;
import minecrafttransportsimulator.mcinterface.WrapperPlayer;
import minecrafttransportsimulator.mcinterface.WrapperWorld;

public class BlockFuelPump extends ABlockBaseDecor implements IBlockFluidTankProvider{
	
	public BlockFuelPump(){
		super();
	}
	
    @Override
	public TileEntityFuelPump createTileEntity(WrapperWorld world, Point3d position, WrapperPlayer placingPlayer, WrapperNBT data){
		return new TileEntityFuelPump(world, position, placingPlayer, data);
	}
}
