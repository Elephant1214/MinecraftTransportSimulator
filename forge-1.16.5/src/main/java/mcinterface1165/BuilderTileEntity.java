package mcinterface1165;

import minecrafttransportsimulator.MtsInfo;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.blocks.components.ABlockBaseTileEntity;
import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Tickable;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for the MC Tile Entity class   This class interfaces with all the MC-specific
 * code, and is constructed on the server automatically by MC.  After construction, a tile entity
 * class that extends {@link ATileEntityBase} should be assigned to it.  This is either
 * done manually on the first placement, or automatically via loading from NBT.
 * <br><br>
 * Of course, one might ask, "why not just construct the TE class when we construct this one?".
 * That's a good point, but MC doesn't work like that.  MC waits to assign the world and position
 * to TEs, so if we construct our TE right away, we'll end up with TONs of null checks.  To avoid this,
 * we only construct our TE after the world and position get assigned, and if we have NBT
 * At that point, we make the TE if we're on the server.  If we're on the client, we always way
 * for NBT, as we need to sync with the server's data.
 *
 * @author don_bruce
 */
public class BuilderTileEntity extends BlockEntity implements Tickable {
    protected static final DeferredRegister<BlockEntityType<?>> TILE_ENTITIES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, MtsInfo.MOD_ID);
    protected static RegistryObject<BlockEntityType<BuilderTileEntity>> TE_TYPE;
    /**
     * Players requesting data for this builder.  This is populated by packets sent to the server.  Each tick players in this list are
     * sent data about this builder, and the list cleared.  Done this way to prevent the server from trying to handle the packet before
     * it has created the entity, as the entity is created on the update call, but the packet might get here due to construction.
     **/
    protected final List<IWrapperPlayer> playersRequestingData = new ArrayList<>();
    protected ATileEntityBase<?> tileEntity;
    /**
     * Data loaded on last NBT call.  Saved here to prevent loading of things until the update method.  This prevents
     * loading entity data when this entity isn't being ticked.  Some mods love to do this by making a lot of entities
     * to do their funky logic.  I'm looking at YOU The One Probe!  This should be either set by NBT loaded from disk
     * on servers, or set by packet on clients.
     */
    protected NbtCompound lastLoadedNBT;
    /**
     * Set to true when NBT is loaded on servers from disk, or when NBT arrives from clients on servers.  This is set on the update loop when data is
     * detected from server NBT loading, but for clients this is set when a data packet arrives.  This prevents loading client-based NBT before
     * the packet arrives, which is possible if a partial NBT load is performed by the core game or a mod.
     **/
    protected boolean loadFromSavedNBT;
    /**
     * Set to true when loaded NBT is parsed and loaded.  This is done to prevent re-parsing of NBT from triggering a second load command.
     **/
    protected boolean loadedFromSavedNBT;
    /**
     * This flag is true if we need to get server data for syncing.  Set on construction tick, but only used on clients.
     **/
    private boolean needDataFromServer = true;

    public BuilderTileEntity() {
        this(TE_TYPE.get());
        //Blank constructor for MC.
    }

    public BuilderTileEntity(BlockEntityType<?> teType) {
        super(teType);
        //Override type constructor.
    }

    @Override
    public void tick() {
        //World and pos might be null on first few scans.
        if (this.world != null && this.pos != null) {
            if (this.tileEntity != null && !this.loadedFromSavedNBT) {
                //If we are on the server, set the NBT flag.
                if (this.lastLoadedNBT != null && !this.world.isClient) {
                    this.loadFromSavedNBT = true;
                }

                //If we have NBT, and haven't loaded it, do so now.
                //Hold off on loading until blocks load: this can take longer than 1 update if the server/client is laggy.
                if (this.loadFromSavedNBT && this.world.isAreaLoaded(this.pos, 0)) {
                    try {
                        //Get the block that makes this TE and restore it from saved state.
                        WrapperWorld worldWrapper = WrapperWorld.getWrapperFor(this.world);
                        Point3D position = new Point3D(this.pos.getX(), this.pos.getY(), this.pos.getZ());
                        ABlockBaseTileEntity block = (ABlockBaseTileEntity) worldWrapper.getBlock(position);
                        IWrapperNBT data = new WrapperNBT(this.lastLoadedNBT);
                        setTileEntity(block.createTileEntity(worldWrapper, position, null, data.getPackItem(), data));
                        this.tileEntity.world.addEntity(this.tileEntity);
                        this.loadedFromSavedNBT = true;
                        this.lastLoadedNBT = null;
                    } catch (Exception e) {
                        InterfaceManager.coreInterface.logError("Failed to load tile entity on builder from saved NBT.  Did a pack change?");
                        InterfaceManager.coreInterface.logError(e.getMessage());
                        this.world.removeBlock(pos, false);
                    }
                }
            }

            //Now that we have done update/NBT stuff, check for syncing.
            if (this.world.isClient) {
                //No data.  Wait for NBT to be loaded.
                //As we are on a client we need to send a packet to the server to request NBT data.
                ///Although we could call this in the constructor, Minecraft changes the
                //entity IDs after spawning and that fouls things up.
                if (this.needDataFromServer) {
                    InterfaceManager.packetInterface.sendToServer(new PacketEntityCSHandshakeClient(InterfaceManager.clientInterface.getClientPlayer(), this));
                    this.needDataFromServer = false;
                }
            } else {
                //Send any packets to clients that requested them.
                if (!this.playersRequestingData.isEmpty()) {
                    IWrapperNBT data = InterfaceManager.coreInterface.getNewNBTWrapper();
                    writeNbt(((WrapperNBT) data).tag);

                    for (IWrapperPlayer player : this.playersRequestingData) {
                        player.sendPacket(new PacketEntityCSHandshakeServer(this, data));

                    }
                    this.playersRequestingData.clear();
                }
            }
        }
    }

    /**
     * Called to set the tileEntity on this builder, allows for sub-classes to do logic too.
     **/
    protected void setTileEntity(ATileEntityBase<?> tile) {
        this.tileEntity = tile;
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        //Invalidate happens when we break the block this TE is on.
        if (this.tileEntity != null) {
            this.tileEntity.remove();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        //Catch unloaded TEs from when the chunk goes away and kill them.
        //MC forgets to do this normally.
        if (this.tileEntity != null && this.tileEntity.isValid) {
            markRemoved();
        }
    }

    @Override
    public void fromTag(BlockState state, NbtCompound tag) {
        super.fromTag(state, tag);
        //Don't directly load the TE here.  This causes issues because Minecraft loads TEs before blocks.
        //This is horridly stupid, because then you can't get the block for the TE, but whatever, Mojang be Mojang.
        this.lastLoadedNBT = tag;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (this.tileEntity != null) {
            this.tileEntity.save(new WrapperNBT(tag));
        } else if (this.lastLoadedNBT != null) {
            //Need to have this here as some mods will load us from NBT and then save us back
            //without ticking.  This causes data loss if we don't merge the last loaded NBT tag.
            //If we did tick, then the last loaded will be null and this doesn't apply.
            tag = this.lastLoadedNBT;
        }
        return tag;
    }
}