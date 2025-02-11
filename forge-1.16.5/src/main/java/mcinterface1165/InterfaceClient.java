package mcinterface1165;

import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.instances.EntityFluidTank;
import minecrafttransportsimulator.guis.components.AGUIBase;
import minecrafttransportsimulator.guis.instances.GUIPackMissing;
import minecrafttransportsimulator.mcinterface.*;
import minecrafttransportsimulator.packloading.PackParser;
import minecrafttransportsimulator.rendering.RenderText;
import minecrafttransportsimulator.systems.CameraSystem.CameraMode;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.ControlSystem;
import minecrafttransportsimulator.systems.LanguageSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.fluid.Fluid;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@EventBusSubscriber(Dist.CLIENT)
public class InterfaceClient implements IInterfaceClient {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private static CameraMode actualCameraMode;
    private static CameraMode cameraModeRequest;
    private static int ticksToCullingWarning = 200;

    @Override
    public boolean isGamePaused() {
        return this.client.isPaused();
    }

    @Override
    public String getLanguageName() {
        if (this.client.getLanguageManager() != null) {
            return this.client.getLanguageManager().getLanguage().getCode();
        } else {
            return "en_us";
        }
    }

    @Override
    public List<String> getAllLanguages() {
        List<String> list = new ArrayList<>();
        this.client.getLanguageManager().getAllLanguages().forEach(language -> list.add(language.getCode()));
        return list;
    }

    @Override
    public String getFluidName(String fluidID, String fluidMod) {
        for (Entry<RegistryKey<Fluid>, Fluid> fluidEntry : ForgeRegistries.FLUIDS.getEntries()) {
            Identifier fluidLocation = fluidEntry.getKey().getValue();
            if ((fluidMod.equals(EntityFluidTank.WILDCARD_FLUID_MOD) || fluidLocation.getNamespace().equals(fluidMod)) && fluidLocation.getPath().equals(fluidID)) {
                return new TranslatableText(fluidEntry.getValue().getAttributes().getTranslationKey()).getString();
            }
        }
        return "INVALID";
    }

    @Override
    public Map<String, String> getAllFluidNames() {
        Map<String, String> fluidIDsToNames = new HashMap<>();
        for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
            fluidIDsToNames.put(fluid.getRegistryName().getPath(), new FluidStack(fluid, 1).getDisplayName().getString());
        }
        return fluidIDsToNames;
    }

    @Override
    public boolean isChatOpen() {
        return this.client.currentScreen instanceof ChatScreen;
    }

    @Override
    public boolean isGUIOpen() {
        return this.client.currentScreen != null;
    }

    @Override
    public CameraMode getCameraMode() {
        return actualCameraMode;
    }

    @Override
    public void setCameraMode(CameraMode mode) {
        cameraModeRequest = mode;
    }

    @Override
    public int getCameraDefaultZoom() {
        return 0;
    }

    @Override
    public long getPackedDisplaySize() {
        return (((long) this.client.getWindow().getScaledWidth()) << Integer.SIZE) | (this.client.getWindow().getScaledHeight() & 0xffffffffL);
    }

    @Override
    public float getFOV() {
        return (float) this.client.options.fov;
    }

    @Override
    public void setFOV(float setting) {
        this.client.options.fov = setting;
    }

    @Override
    public float getMouseSensitivity() {
        return (float) this.client.options.mouseSensitivity;
    }

    @Override
    public void setMouseSensitivity(float setting) {
        this.client.options.mouseSensitivity = setting;
    }

    @Override
    public void closeGUI() {
        this.client.openScreen(null);
    }

    @Override
    public void setActiveGUI(AGUIBase gui) {
        this.client.openScreen(new BuilderGUI(gui));
    }

    @Override
    public WrapperWorld getClientWorld() {
        return WrapperWorld.getWrapperFor(this.client.world);
    }

    @Override
    public WrapperPlayer getClientPlayer() {
        return WrapperPlayer.getWrapperFor(this.client.player);
    }

    @Override
    public Point3D getCameraPosition() {
        Vec3d cameraOffset = this.client.gameRenderer.getCamera().getPos();
        mutablePosition.set(cameraOffset.x, cameraOffset.y, cameraOffset.z);
        return mutablePosition;
    }

    private static final Point3D mutablePosition = new Point3D();

    @Override
    public void playBlockBreakSound(Point3D position) {
        BlockPos pos = new BlockPos(position.x, position.y, position.z);
        if (!this.client.world.isAir(pos)) {
            BlockSoundGroup soundType = this.client.world.getBlockState(pos).getBlock().getSoundType(this.client.world.getBlockState(pos), this.client.player.world, pos, null);
            this.client.world.playSound(this.client.player, pos, soundType.getBreakSound(), SoundCategory.BLOCKS, soundType.getVolume(), soundType.getPitch());
        }
    }

    @Override
    public List<String> getTooltipLines(IWrapperItemStack stack) {
        List<String> tooltipText = new ArrayList<>();
        List<Text> tooltipLines = ((WrapperItemStack) stack).stack.getTooltip(this.client.player, this.client.options.advancedItemTooltips ? TooltipContext.Default.ADVANCED : TooltipContext.Default.NORMAL);
        //Add grey formatting text to non-first line tooltips.
        for (Text component : tooltipLines) {
            Style style = component.getStyle();
            String stringToAdd = "";
            if (style.isBold()) {
                stringToAdd += RenderText.FORMATTING_CHAR + RenderText.BOLD_FORMATTING_CHAR;
            }
            if (style.isItalic()) {
                stringToAdd += RenderText.FORMATTING_CHAR + RenderText.ITALIC_FORMATTING_CHAR;
            }
            if (style.isUnderlined()) {
                stringToAdd += RenderText.FORMATTING_CHAR + RenderText.UNDERLINE_FORMATTING_CHAR;
            }
            if (style.isStrikethrough()) {
                stringToAdd += RenderText.FORMATTING_CHAR + RenderText.STRIKETHROUGH_FORMATTING_CHAR;
            }
            if (style.isObfuscated()) {
                stringToAdd += RenderText.FORMATTING_CHAR + RenderText.RANDOM_FORMATTING_CHAR;
            }
            if (style.getColor() != null) {
                Formatting legacyColor = null;
                for (Formatting format : Formatting.values()) {
                    if (format.isColor()) {
                        if (style.getColor().equals(TextColor.fromFormatting(format))) {
                            legacyColor = format;
                            break;
                        }
                    }
                }
                if (legacyColor != null) {
                    stringToAdd += RenderText.FORMATTING_CHAR + Integer.toHexString(legacyColor.ordinal());
                }
            }
            tooltipText.add(stringToAdd + component.getString());
        }
        return tooltipText;
    }

    /**
     * Tick client-side entities like bullets and particles.
     * These don't get ticked normally due to the world tick event
     * not being called on clients.
     */
    @SubscribeEvent
    public static void onIVClientTick(TickEvent.ClientTickEvent event) {
        IWrapperPlayer player = InterfaceManager.clientInterface.getClientPlayer();
        if (!InterfaceManager.clientInterface.isGamePaused() && player != null) {
            AWrapperWorld world = InterfaceManager.clientInterface.getClientWorld();
            if (world != null) {
                if (event.phase.equals(Phase.START)) {
                    if (!player.isSpectator()) {
                        //Handle controls.  This has to happen prior to vehicle updates to ensure click handling is based on current position of the player.
                        ControlSystem.controlGlobal(player);
                        if (((WrapperPlayer) player).player.age % 100 == 0) {
                            if (!InterfaceManager.clientInterface.isGUIOpen() && !PackParser.arePacksPresent()) {
                                new GUIPackMissing();
                            }
                        }
                    }

                    //Need to update world brightness since sky darken isn't calculated normally on clients.
                    ((WrapperWorld) world).world.calculateAmbientDarkness();

                    world.tickAll(true);

                    //Complain about compats at 10 second mark.
                    if (ConfigSystem.settings.general.performModCompatFunctions.value) {
                        if (ticksToCullingWarning > 0) {
                            if (--ticksToCullingWarning == 0) {
                                if (InterfaceManager.coreInterface.isModPresent("entityculling")) {
                                    player.displayChatMessage(LanguageSystem.SYSTEM_DEBUG, "IV HAS DETECTED THAT ENTITY CULLING MOD IS PRESENT.  THIS MOD CULLS ALL IV VEHICLES UNLESS \"mts:builder_existing\", \"mts:builder_rendering\", AND \"mts:builder_seat\" ARE ADDED TO THE WHITELIST.");
                                }
                                if (InterfaceManager.coreInterface.isModPresent("modernfix")) {
                                    player.displayChatMessage(LanguageSystem.SYSTEM_DEBUG, "IV HAS DETECTED THAT MODERNFIX MOD IS PRESENT.  IF DYNAMIC RESOURCES IS SET TO TRUE IV ITEMS WILL NOT RENDER PROPERLY.");
                                }
                            }
                        }
                    }
                } else {
                    world.tickAll(false);

                    //Handle camera requests.
                    if (cameraModeRequest != null) {
                        switch (cameraModeRequest) {
                            case FIRST_PERSON: {
                                MinecraftClient.getInstance().options.setPerspective(Perspective.FIRST_PERSON);
                                break;
                            }
                            case THIRD_PERSON: {
                                MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_BACK);
                                break;
                            }
                            case THIRD_PERSON_INVERTED: {
                                MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                                break;
                            }
                        }
                        cameraModeRequest = null;
                    }

                    //Update camera state, since this can change depending on tick if we check during renders.
                    Perspective cameraModeEnum = MinecraftClient.getInstance().options.getPerspective();
                    switch (cameraModeEnum) {
                        case FIRST_PERSON: {
                            actualCameraMode = CameraMode.FIRST_PERSON;
                            break;
                        }
                        case THIRD_PERSON_BACK: {
                            actualCameraMode = CameraMode.THIRD_PERSON;
                            break;
                        }
                        case THIRD_PERSON_FRONT: {
                            actualCameraMode = CameraMode.THIRD_PERSON_INVERTED;
                            break;
                        }
                    }
                }
            }
        }
    }
}
