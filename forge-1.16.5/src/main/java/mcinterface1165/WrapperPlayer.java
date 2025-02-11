package mcinterface1165;

import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.instances.ItemItem;
import minecrafttransportsimulator.jsondefs.JSONItem.ItemComponentType;
import minecrafttransportsimulator.mcinterface.*;
import minecrafttransportsimulator.packets.components.APacketBase;
import minecrafttransportsimulator.systems.LanguageSystem.LanguageEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber
public class WrapperPlayer extends WrapperEntity implements IWrapperPlayer {
    private static final Map<PlayerEntity, WrapperPlayer> playerClientWrappers = new HashMap<>();
    private static final Map<PlayerEntity, WrapperPlayer> playerServerWrappers = new HashMap<>();

    protected final PlayerEntity player;

    protected WrapperPlayer(PlayerEntity player) {
        super(player);
        this.player = player;
    }

    /**
     * Returns a wrapper instance for the passed-in player instance.
     * Null may be passed-in safely to ease function-forwarding.
     * Note that the wrapped player class MAY be side-specific, so avoid casting
     * the wrapped entity directly if you aren't sure what its class is.
     * Wrapper is cached to avoid re-creating the wrapper each time it is requested.
     */
    public static WrapperPlayer getWrapperFor(PlayerEntity player) {
        if (player != null) {
            Map<PlayerEntity, WrapperPlayer> playerWrappers = player.world.isClient ? playerClientWrappers : playerServerWrappers;
            WrapperPlayer wrapper = playerWrappers.get(player);
            if (wrapper == null || !wrapper.isValid() || player != wrapper.player) {
                wrapper = new WrapperPlayer(player);
                playerWrappers.put(player, wrapper);
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
            playerClientWrappers.keySet().removeIf(entity1 -> event.getWorld() == entity1.world);
        } else {
            playerServerWrappers.keySet().removeIf(entity1 -> event.getWorld() == entity1.world);
        }
    }

    @Override
    public double getSeatOffset() {
        //Vanilla players have a -0.35 offset, which is horridly wrong.
        //Player legs are 12 pixels, but the player model has a funky scale.
        //It's supposed to be 32px, but is scaled to 30px, so we need to factor that here.
        //Only return this offset if super returns a non-zero number, which indicates we're in a sitting seat.
        if (super.getSeatOffset() != 0) {
            return (-12D / 16D) * (30D / 32D);
        } else {
            return 0;
        }
    }

    @Override
    public boolean isOP() {
        return player.getServer() == null || player.getServer().getPlayerManager().getOpList().get(player.getGameProfile()) != null || player.getServer().isSinglePlayer();
    }

    @Override
    public void displayChatMessage(LanguageEntry language, Object... args) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new LiteralText(String.format(language.getCurrentValue(), args)));
    }

    @Override
    public boolean isCreative() {
        return this.player.isCreative();
    }

    @Override
    public boolean isSpectator() {
        return this.player.isSpectator();
    }

    @Override
    public boolean isSneaking() {
        return this.player.isInSneakingPose();
    }

    @Override
    public boolean isRightHanded() {
        return this.player.getMainArm() == Arm.RIGHT;
    }

    @Override
    public IWrapperEntity getLeashedEntity() {
        for (MobEntity mobEntity : this.player.world.getEntitiesIncludingUngeneratedChunks(MobEntity.class, new Box(this.player.getPos().x - 7.0D, this.player.getPos().y - 7.0D, this.player.getPos().z - 7.0D, this.player.getPos().x + 7.0D, this.player.getPos().y + 7.0D, this.player.getPos().z + 7.0D))) {
            if (mobEntity.isLeashed() && this.player.equals(mobEntity.getHoldingEntity())) {
                mobEntity.detachLeash(true, !this.player.isCreative());
                return WrapperEntity.getWrapperFor(mobEntity);
            }
        }
        return null;
    }

    @Override
    public boolean isHoldingItemType(ItemComponentType type) {
        AItemBase heldItem = getHeldItem();
        return heldItem instanceof ItemItem && ((ItemItem) heldItem).definition.item.type.equals(type);
    }

    @Override
    public AItemBase getHeldItem() {
        Item heldItem = this.player.getActiveItem().getItem();
        return heldItem instanceof IBuilderItemInterface ? ((IBuilderItemInterface) heldItem).getWrappedItem() : null;
    }

    @Override
    public IWrapperItemStack getHeldStack() {
        return new WrapperItemStack(this.player.inventory.getStack(getHotbarIndex()));
    }

    @Override
    public void setHeldStack(IWrapperItemStack stack) {
        this.player.inventory.setStack(getHotbarIndex(), ((WrapperItemStack) stack).stack);
    }

    @Override
    public int getHotbarIndex() {
        return this.player.inventory.selectedSlot;
    }

    @Override
    public IWrapperInventory getInventory() {
        return new WrapperInventory(this.player.inventory) {
            @Override
            public int getSize() {
                return player.inventory.main.size();
            }
        };
    }

    @Override
    public void sendPacket(APacketBase packet) {
        InterfaceManager.packetInterface.sendToPlayer(packet, this);
    }

    @Override
    public void openCraftingGUI() {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((containerID, playerInventory, player) -> new CraftingScreenHandler(containerID, playerInventory, ScreenHandlerContext.create(player.world, player.getBlockPos())) {
            @Override
            public boolean canUse(PlayerEntity pPlayer) {
                return true;
            }
        }, LiteralText.EMPTY));
    }
}