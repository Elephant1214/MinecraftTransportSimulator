package mcinterface1165;

import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.components.AItemPack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for a MC creative tabs.  This class interfaces with the MC creative tab system,
 * allowing for items to be stored in it for rendering via the tab.  The item rendered as
 * the tab icon normally cycles between all items in the tab, but can be set to a member
 * item of the tab if desired.
 *
 * @author don_bruce
 */
public class BuilderCreativeTab extends ItemGroup {
    /**
     * Map of created tabs names linked to their builder instances.  Used for interface operations.
     **/
    protected static final Map<String, BuilderCreativeTab> createdTabs = new HashMap<>();

    private final String label;
    private final AItemPack<?> tabItem;
    private final List<Item> items = new ArrayList<>();

    BuilderCreativeTab(String name, AItemPack<?> tabItem) {
        super(name);
        this.label = name;
        //Need to delay turning this into a MC item since we may not yet have created a builder.
        this.tabItem = tabItem;
    }

    /**
     * Adds the passed-in item to this tab.
     */
    public void addItem(AItemBase item, BuilderItem mcItem) {
        this.items.add(mcItem);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public Text getTranslationKey() {
        return new LiteralText(label);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public ItemStack createIcon() {
        return this.tabItem != null ? new ItemStack(BuilderItem.itemMap.get(this.tabItem)) : null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public ItemStack getIcon() {
        if (this.tabItem != null) {
            return super.getIcon();
        } else {
            int randomIcon = (int) System.currentTimeMillis() / 1000 % this.items.size();
            return new ItemStack(this.items.get(randomIcon));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendStacks(DefaultedList<ItemStack> givenList) {
        //This is needed to re-sort the items here to get them in the correct order.
        //MC will re-order these by ID if we let it.  To prevent this, we swap MC's
        //internal list with our own, which ensures that the order is the order
        //we did registration in.
        givenList.clear();
        for (Item item : this.items) {
            item.appendStacks(this, givenList);
        }
    }
}
