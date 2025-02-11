package mcinterface1182;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.mcinterface.IWrapperItemStack;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

class WrapperNBT implements IWrapperNBT {
    protected final NbtCompound nbt;

    protected WrapperNBT() {
        this.nbt = new NbtCompound();
    }

    protected WrapperNBT(NbtCompound nbt) {
        this.nbt = nbt;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WrapperNBT && this.nbt.equals(((WrapperNBT) obj).nbt);
    }

    @Override
    public boolean getBoolean(String name) {
        return this.nbt.getBoolean(name);
    }

    @Override
    public void setBoolean(String name, boolean value) {
        if (value) {
            this.nbt.putBoolean(name, value);
        } else {
            this.nbt.remove(name);
        }
    }

    @Override
    public int getInteger(String name) {
        return this.nbt.getInt(name);
    }

    @Override
    public void setInteger(String name, int value) {
        if (value != 0) {
            this.nbt.putInt(name, value);
        } else {
            this.nbt.remove(name);
        }
    }

    @Override
    public double getDouble(String name) {
        return this.nbt.getDouble(name);
    }

    @Override
    public void setDouble(String name, double value) {
        if (value != 0) {
            this.nbt.putDouble(name, value);
        } else {
            this.nbt.remove(name);
        }
    }

    @Override
    public String getString(String name) {
        return this.nbt.getString(name);
    }

    @Override
    public void setString(String name, String value) {
        this.nbt.putString(name, value);
    }

    @Override
    public List<String> getStrings(String name) {
        return this.getStrings(name, getInteger(name + "count"));
    }

    @Override
    public List<String> getStrings(String name, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            values.add(getString(name + i));
        }
        return values;
    }

    @Override
    public void setStrings(String name, Collection<String> values) {
        setInteger(name + "count", values.size());
        int index = 0;
        for (String value : values) {
            setString(name + index++, value);
        }
    }

    @Override
    public UUID getUUID(String name) {
        return this.nbt.contains(name) ? UUID.fromString(this.nbt.getString(name)) : null;
    }

    @Override
    public void setUUID(String name, UUID value) {
        this.nbt.putString(name, value.toString());
    }

    @Override
    public List<IWrapperItemStack> getStacks(int count) {
        List<IWrapperItemStack> stacks = new ArrayList<>();
        DefaultedList<ItemStack> mcList = DefaultedList.ofSize(count, ItemStack.EMPTY);
        Inventories.readNbt(this.nbt, mcList);
        for (ItemStack stack : mcList) {
            stacks.add(new WrapperItemStack(stack));
        }
        return stacks;
    }

    @Override
    public void setStacks(List<IWrapperItemStack> stacks) {
        DefaultedList<ItemStack> mcList = DefaultedList.of();
        for (IWrapperItemStack stack : stacks) {
            mcList.add(((WrapperItemStack) stack).stack);
        }
        Inventories.writeNbt(this.nbt, mcList);
    }

    @Override
    public Point3D getPoint3d(String name) {
        return new Point3D(getDouble(name + "x"), getDouble(name + "y"), getDouble(name + "z"));
    }

    @Override
    public void setPoint3d(String name, Point3D value) {
        if (!value.isZero()) {
            setDouble(name + "x", value.x);
            setDouble(name + "y", value.y);
            setDouble(name + "z", value.z);
        }
    }

    @Override
    public List<Point3D> getPoint3ds(String name) {
        List<Point3D> values = new ArrayList<>();
        int count = getInteger(name + "count");
        for (int i = 0; i < count; ++i) {
            Point3D point = getPoint3d(name + i);
            if (!point.isZero()) {
                values.add(point);
            }
        }
        return values;
    }

    @Override
    public void setPoint3ds(String name, Collection<Point3D> values) {
        setInteger(name + "count", values.size());
        int index = 0;
        for (Point3D value : values) {
            setPoint3d(name + index++, value);
        }
    }

    @Override
    public Point3D getPoint3dCompact(String name) {
        return new Point3D(getInteger(name + "x"), getInteger(name + "y"), getInteger(name + "z"));
    }

    @Override
    public void setPoint3dCompact(String name, Point3D value) {
        if (!value.isZero()) {
            setInteger(name + "x", (int) Math.floor(value.x));
            setInteger(name + "y", (int) Math.floor(value.y));
            setInteger(name + "z", (int) Math.floor(value.z));
        }
    }

    @Override
    public List<Point3D> getPoint3dsCompact(String name) {
        List<Point3D> values = new ArrayList<>();
        int count = getInteger(name + "count");
        for (int i = 0; i < count; ++i) {
            Point3D point = getPoint3dCompact(name + i);
            if (!point.isZero()) {
                values.add(point);
            }
        }
        return values;
    }

    @Override
    public void setPoint3dsCompact(String name, Collection<Point3D> values) {
        setInteger(name + "count", values.size());
        int index = 0;
        for (Point3D value : values) {
            setPoint3dCompact(name + index++, value);
        }
    }

    @Override
    public WrapperNBT getData(String name) {
        return this.nbt.contains(name, 10) ? new WrapperNBT(this.nbt.getCompound(name)) : null;
    }

    @Override
    public void setData(String name, IWrapperNBT value) {
        this.nbt.put(name, ((WrapperNBT) value).nbt);
    }

    @Override
    public boolean hasKey(String name) {
        return this.nbt.contains(name);
    }

    @Override
    public void deleteEntry(String name) {
        this.nbt.remove(name);
    }

    @Override
    public Set<String> getAllNames() {
        return this.nbt.getKeys();
    }
}
