package minecrafttransportsimulator.guis.instances;

import minecrafttransportsimulator.baseclasses.ColorRGB;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import minecrafttransportsimulator.guis.components.*;
import minecrafttransportsimulator.items.components.AItemPack;
import minecrafttransportsimulator.items.components.AItemSubTyped;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packets.instances.PacketEntityColorChange;
import minecrafttransportsimulator.packloading.PackMaterialComponent;
import minecrafttransportsimulator.packloading.PackParser;
import minecrafttransportsimulator.rendering.RenderText.TextAlignment;

import java.util.ArrayList;
import java.util.List;

/**
 * A GUI that is used to craft vehicle parts and other pack components.  This GUI displays
 * the items required to craft a vehicle, the item that will be crafted, and some properties
 * of that item.  Allows for scrolling via a scroll wheel, and remembers the last item that
 * was selected to allow for faster lookup next time the GUI is opened.
 *
 * @author don_bruce
 */
public class GUIPaintGun extends AGUIBase {
    //Init variables.
    private final AEntityD_Definable<?> entity;
    private final IWrapperPlayer player;
    //Crafting components.
    private final List<GUIComponentItem> craftingItemIcons = new ArrayList<>();
    private final List<GUIComponentCutout> craftingItemBackgrounds = new ArrayList<>();
    //Buttons and labels.
    private GUIComponentLabel partName;
    private GUIComponentButton prevColorButton;
    private GUIComponentButton nextColorButton;
    private GUIComponentButton nextRecipeButton;
    private GUIComponentButton confirmButton;
    private List<PackMaterialComponent> materials;

    //Renders for the item.
    private GUIComponent3DModel modelRender;

    //Runtime variables.	
    private AItemSubTyped<?> currentItem;
    private AItemSubTyped<?> prevSubItem;
    private AItemSubTyped<?> nextSubItem;
    private int recipeIndex;

    public GUIPaintGun(AEntityD_Definable<?> entity, IWrapperPlayer player) {
        super();
        this.entity = entity;
        this.player = player;
        this.currentItem = (AItemSubTyped<?>) PackParser.getItem(entity.definition.packID, entity.definition.systemName, entity.subDefinition.subName);
    }

    @Override
    public void setupComponents() {
        super.setupComponents();
        //Create color navigation section.
        addComponent(prevColorButton = new GUIComponentButton(this, guiLeft + 38, guiTop + 135, 20, 20, 40, 196, 20, 20) {
            @Override
            public void onClicked(boolean leftSide) {
                currentItem = prevSubItem;
                recipeIndex = 0;
                updateNames();
            }
        });
        addComponent(nextColorButton = new GUIComponentButton(this, guiLeft + 160, guiTop + 135, 20, 20, 60, 196, 20, 20) {
            @Override
            public void onClicked(boolean leftSide) {
                currentItem = nextSubItem;
                recipeIndex = 0;
                updateNames();
            }
        });
        addComponent(nextRecipeButton = new GUIComponentButton(this, guiLeft + 233, guiTop + 100, 20, 20, 80, 196, 20, 20) {
            @Override
            public void onClicked(boolean leftSide) {
                if (++recipeIndex == currentItem.subDefinition.extraMaterialLists.size()) {
                    recipeIndex = 0;
                }
                updateNames();
            }
        });
        addComponent(partName = new GUIComponentLabel(guiLeft + 60, guiTop + 120, ColorRGB.WHITE, "", TextAlignment.LEFT_ALIGNED, 1.0F, 98));

        //Create the crafting item slots.  8 16X16 slots (8X2) need to be made here.
        craftingItemIcons.clear();
        craftingItemBackgrounds.clear();
        for (byte i = 0; i < 4 * 2; ++i) {
            GUIComponentItem craftingItem = new GUIComponentItem(guiLeft + 225 + GUIComponentButton.ITEM_BUTTON_SIZE * (i / 4), guiTop + 26 + GUIComponentButton.ITEM_BUTTON_SIZE * (i % 4), 1.0F);
            GUIComponentCutout itemBackground = new GUIComponentCutout(this, craftingItem.constructedX, craftingItem.constructedY, craftingItem.width, craftingItem.height, 160, 236, 20, 20);
            itemBackground.visible = false;
            addComponent(craftingItem);
            addComponent(itemBackground);
            craftingItemIcons.add(craftingItem);
            craftingItemBackgrounds.add(itemBackground);
        }

        //Create the OBJ render.
        addComponent(modelRender = new GUIComponent3DModel(guiLeft + 109, guiTop + 57, 32.0F, true, true, false));

        //Create the confirm button.
        addComponent(confirmButton = new GUIComponentButton(this, guiLeft + 99, guiTop + 167, 20, 20, 20, 196, 20, 20) {
            @Override
            public void onClicked(boolean leftSide) {
                InterfaceManager.packetInterface.sendToServer(new PacketEntityColorChange(entity, player, currentItem, recipeIndex));
                close();
            }
        });

        //Update the names now that we have everything put together.
        updateNames();
    }

    @Override
    public void setStates() {
        super.setStates();
        //Set buttons based on if we have prev or next items.
        prevColorButton.enabled = prevSubItem != null;
        nextColorButton.enabled = nextSubItem != null;

        // FIXME: currentItem not checked for null here but checked later?
        //Enable repair recipe button if we have multiple indexes.
        nextRecipeButton.enabled = currentItem.subDefinition.extraMaterialLists.size() > 1;

        //Set confirm button based on if player has materials.
        confirmButton.enabled = currentItem != null && (player.isCreative() || (materials != null && player.getInventory().hasMaterials(materials)));

        //Set materials.
        //Get the offset index based on the clock-time and the number of materials.
        if (materials != null) {
            for (byte i = 0; i < craftingItemIcons.size(); ++i) {
                if (i < materials.size()) {
                    craftingItemIcons.get(i).stacks = materials.get(i).possibleItems;
                    craftingItemBackgrounds.get(i).visible = !player.isCreative() && inClockPeriod(20, 10) && player.getInventory().hasSpecificMaterial(currentItem, recipeIndex, i, false, true, false, true);
                } else {
                    craftingItemIcons.get(i).stacks = null;
                    craftingItemBackgrounds.get(i).visible = false;
                }
            }
        } else {
            craftingItemIcons.forEach(icon -> icon.stacks = null);
        }
    }

    @Override
    public int getWidth() {
        return 327;
    }

    @Override
    public int getHeight() {
        return 196;
    }

    @Override
    protected String getTexture() {
        return "mts:textures/guis/repainting.png";
    }

    /**
     * Loop responsible for updating pack/part names whenever an action occurs.
     * Looks through all items in the list that was passed-in on GUI construction time and
     * uses the order to determine which pack/item to scroll to when a button is clicked.
     * Sets the variables to be used on a button action, so once an action is performed this
     * logic MUST be called to update the button action states!
     */
    private void updateNames() {
        //Get all pack indexes.		
        List<AItemPack<?>> packItems = PackParser.getAllItemsForPack(currentItem.definition.packID, true);
        int currentItemIndex = packItems.indexOf(currentItem);

        //Loop forwards in our pack to find the next item in that pack.
        nextSubItem = null;
        if (currentItemIndex < packItems.size()) {
            for (int i = currentItemIndex + 1; i < packItems.size() && nextSubItem == null; ++i) {
                if (packItems.get(i).definition.systemName.equals(currentItem.definition.systemName)) {
                    nextSubItem = (AItemSubTyped<?>) packItems.get(i);
                    break;
                }
            }
        }

        //Loop backwards in our pack to find the prev item in that pack.
        prevSubItem = null;
        if (currentItemIndex > 0) {
            for (int i = currentItemIndex - 1; i >= 0 && prevSubItem == null; --i) {
                if (packItems.get(i).definition.systemName.equals(currentItem.definition.systemName)) {
                    prevSubItem = (AItemSubTyped<?>) packItems.get(i);
                    break;
                }
            }
        }

        //All item bits are now set and updated.  Update info labels and item icons.
        partName.text = currentItem.getItemName();

        //Parse crafting items and set icon items.
        //Check all possible recipes, since some might be for other mods or versions.
        StringBuilder errorMessage = new StringBuilder();
        do {
            materials = PackMaterialComponent.parseFromJSON(currentItem, recipeIndex, false, true, false, false);
            if (materials == null) {
                if (++recipeIndex == currentItem.subDefinition.extraMaterialLists.size()) {
                    recipeIndex = 0;
                }
                errorMessage.append(PackMaterialComponent.lastErrorMessage).append("\n");
                if (recipeIndex == 0) {
                    InterfaceManager.coreInterface.logError(errorMessage.toString());
                    break;
                }
            }
        } while (materials == null);

        //Set model render properties.
        modelRender.modelLocation = currentItem.definition.getModelLocation(currentItem.subDefinition);
        modelRender.textureLocation = currentItem.definition.getTextureLocation(currentItem.subDefinition);
    }
}