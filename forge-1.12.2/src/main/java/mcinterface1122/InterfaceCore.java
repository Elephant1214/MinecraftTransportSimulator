package mcinterface1122;

import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.mcinterface.IInterfaceCore;
import minecrafttransportsimulator.mcinterface.IWrapperItemStack;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.oredict.OreDictionary;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class InterfaceCore implements IInterfaceCore {
    @Override
    public boolean isGameFlattened() {
        return false;
    }

    @Override
    public boolean isModPresent(String modID) {
        return Loader.isModLoaded(modID);
    }

    @Override
    public boolean isFluidValid(String fluidID) {
        return FluidRegistry.isFluidRegistered(fluidID);
    }

    @Override
    public String getModName(String modID) {
        return Loader.instance().getIndexedModList().get(modID).getName();
    }

    @Override
    public InputStream getPackResource(String resource) {
        return InterfaceCore.class.getResourceAsStream(resource);
    }

    @Override
    public void logError(String message) {
        InterfaceLoader.LOGGER.error("MTSERROR: " + message);
    }

    @Override
    public IWrapperNBT getNewNBTWrapper() {
        return new WrapperNBT();
    }

    @Override
    public IWrapperItemStack getAutoGeneratedStack(AItemBase item, IWrapperNBT data) {
        WrapperItemStack newStack = new WrapperItemStack(new ItemStack(BuilderItem.itemMap.get(item)));
        newStack.setData(data);
        return newStack;
    }

    @Override
    public IWrapperItemStack getStackForProperties(String name, int meta, int qty) {
        Item item = Item.getByNameOrId(name);
        if (item != null) {
            return new WrapperItemStack(new ItemStack(Item.getByNameOrId(name), qty, meta));
        } else {
            return new WrapperItemStack(ItemStack.EMPTY.copy());
        }
    }

    @Override
    public String getStackItemName(IWrapperItemStack stack) {
        return Item.REGISTRY.getNameForObject(((WrapperItemStack) stack).stack.getItem()).toString();
    }

    @Override
    public boolean isOredictMatch(IWrapperItemStack stackA, IWrapperItemStack stackB) {
        return OreDictionary.itemMatches(((WrapperItemStack) stackA).stack, ((WrapperItemStack) stackB).stack, false);
    }

    @Override
    public List<IWrapperItemStack> getOredictMaterials(String oreName, int stackSize) {
        NonNullList<ItemStack> oreDictStacks = OreDictionary.getOres(oreName, false);
        List<IWrapperItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : oreDictStacks) {
            if (stack.getMetadata() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE) {
                NonNullList<ItemStack> oreDictSubStacks = NonNullList.create();
                stack.getItem().getSubItems(CreativeTabs.SEARCH, oreDictSubStacks);
                for (ItemStack subStack : oreDictSubStacks) {
                    ItemStack editedStack = subStack.copy();
                    editedStack.setCount(stackSize);
                    stacks.add(new WrapperItemStack(editedStack));
                }

            } else {
                ItemStack editedStack = stack.copy();
                editedStack.setCount(stackSize);
                stacks.add(new WrapperItemStack(editedStack));
            }

        }
        return stacks;
    }
}
