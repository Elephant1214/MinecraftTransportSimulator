package mcinterface1122;

import minecrafttransportsimulator.MtsInfo;
import minecrafttransportsimulator.baseclasses.EntityManager;
import minecrafttransportsimulator.guis.instances.GUIConfig;
import minecrafttransportsimulator.jsondefs.JSONConfigClient.ConfigJoystick;
import minecrafttransportsimulator.mcinterface.IInterfaceInput;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packloading.JSONParser;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.ControlSystem.ControlsJoystick;
import minecrafttransportsimulator.systems.LanguageSystem;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Controllers;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.*;
import java.util.Map.Entry;

@EventBusSubscriber(Side.CLIENT)
public class InterfaceInput implements IInterfaceInput {
    private static final Map<String, Integer> joystickNameCounters = new HashMap<>();
    //Normal mode joystick variables.
    private static final Map<String, org.lwjgl.input.Controller> joystickMap = new LinkedHashMap<>();
    private static final Map<String, Integer> joystickAxisCountMap = new LinkedHashMap<>();
    //Classic mode joystick variables.
    private static final Map<String, net.java.games.input.Controller> classicJoystickMap = new LinkedHashMap<>();
    //Common variables.
    private static KeyBinding configKey;
    private static KeyBinding importKey;
    //Mouse variables.
    private static boolean betterCombatDetected;
    private static boolean leftMouseButtonDown;
    private static boolean rightMouseButtonDown;
    //Joystick variables.
    private static boolean runningJoystickThread = false;
    private static boolean runningClassicMode = false;
    private static boolean joystickLoadingAttempted = false;
    private static boolean joystickEnabled = false;
    private static boolean joystickBlocked = false;

    /**
     * Stores mouse presses, since stupid mods take them from us.
     * BetterCombat is one such mod.
     */
    @SubscribeEvent
    public static void onIVMouseInput(MouseEvent event) {
        if (betterCombatDetected) {
            int button = event.getButton();
            if (button == 0) {
                leftMouseButtonDown = event.isButtonstate();
            } else if (button == 1) {
                rightMouseButtonDown = event.isButtonstate();
            }
        }
    }

    /**
     * Opens the config screen when the config key is pressed.
     * Also init the joystick system if we haven't already.
     */
    @SubscribeEvent
    public static void onIVKeyInput(InputEvent.KeyInputEvent event) {
        //Check if we switched joystick modes.
        if (runningClassicMode ^ ConfigSystem.client.controlSettings.classicJystk.value) {
            runningClassicMode = ConfigSystem.client.controlSettings.classicJystk.value;
            joystickLoadingAttempted = false;
        }

        //Init joysticks if we haven't already tried or if we switched loaders.
        if (!joystickLoadingAttempted) {
            InterfaceManager.inputInterface.initJoysticks();
            joystickLoadingAttempted = true;
        }

        //Check if we pressed the config or import key.
        if (configKey.isPressed() && !InterfaceManager.clientInterface.isGUIOpen()) {
            new GUIConfig();
        } else if (ConfigSystem.settings.general.devMode.value && importKey.isPressed()) {
            EntityManager.doImports(() -> InterfaceManager.clientInterface.getClientPlayer().displayChatMessage(LanguageSystem.SYSTEM_DEBUG, JSONParser.importAllJSONs(true)));
        }
    }

    @Override
    public int getKeysetID() {
        return 12;
    }

    @Override
    public void initConfigKey() {
        configKey = new KeyBinding(LanguageSystem.GUI_MASTERCONFIG.getCurrentValue(), Keyboard.KEY_P, MtsInfo.MOD_NAME);
        ClientRegistry.registerKeyBinding(configKey);
        importKey = new KeyBinding(LanguageSystem.GUI_IMPORT.getCurrentValue(), Keyboard.KEY_NONE, MtsInfo.MOD_NAME);
        ClientRegistry.registerKeyBinding(importKey);
        betterCombatDetected = InterfaceManager.coreInterface.isModPresent("bettercombatmod");
    }

    @Override
    public void initJoysticks() {
        //Populate the joystick device map.
        //Joystick will be enabled if at least one controller is found.  If none are found, we likely have an error.
        //We can re-try this if the user removes their mouse and we re-run this method.
        if (!runningJoystickThread) {
            runningJoystickThread = true;
            joystickBlocked = true;
            Thread joystickThread = new Thread(() -> {
                try {
                    joystickNameCounters.clear();
                    if (runningClassicMode) {
                        classicJoystickMap.clear();
                        for (Controller joystick : ControllerEnvironment.getDefaultEnvironment().getControllers()) {
                            joystickEnabled = true;
                            if (joystick.getType() != null && !joystick.getType().equals(Controller.Type.MOUSE) && !joystick.getType().equals(Controller.Type.KEYBOARD) && joystick.getName() != null && joystick.getComponents().length != 0) {
                                String joystickName = joystick.getName();

                                //Add an index on this joystick to be sure we don't override multi-component units.
                                if (!joystickNameCounters.containsKey(joystickName)) {
                                    joystickNameCounters.put(joystickName, 0);
                                }
                                classicJoystickMap.put(joystickName + "_" + joystickNameCounters.get(joystickName), joystick);
                                joystickNameCounters.put(joystickName, joystickNameCounters.get(joystickName) + 1);
                            }
                        }
                    } else {
                        if (!Controllers.isCreated()) {
                            Controllers.create();
                        }
                        joystickMap.clear();
                        joystickAxisCountMap.clear();
                        for (int i = 0; i < Controllers.getControllerCount(); ++i) {
                            joystickEnabled = true;
                            org.lwjgl.input.Controller joystick = Controllers.getController(i);
                            if (joystick.getAxisCount() > 0 && joystick.getButtonCount() > 0 && joystick.getName() != null) {
                                String joystickName = joystick.getName();

                                //Add an index on this joystick to be sure we don't override multi-component units.
                                if (!joystickNameCounters.containsKey(joystickName)) {
                                    joystickNameCounters.put(joystickName, 0);
                                }
                                joystickMap.put(joystickName + "_" + joystickNameCounters.get(joystickName), joystick);
                                joystickAxisCountMap.put(joystickName + "_" + joystickNameCounters.get(joystickName), joystick.getAxisCount());
                                joystickNameCounters.put(joystickName, joystickNameCounters.get(joystickName) + 1);
                            }
                        }
                    }

                    //Validate joysticks are valid for this setup by making sure indexes aren't out of bounds.
                    Iterator<Entry<String, ConfigJoystick>> iterator = ConfigSystem.client.controls.joystick.entrySet().iterator();
                    while (iterator.hasNext()) {
                        try {
                            Entry<String, ConfigJoystick> controllerEntry = iterator.next();
                            ControlsJoystick control = ControlsJoystick.valueOf(controllerEntry.getKey().toUpperCase(Locale.ROOT));
                            ConfigJoystick config = controllerEntry.getValue();
                            if (runningClassicMode) {
                                if (classicJoystickMap.containsKey(config.joystickName)) {
                                    if (classicJoystickMap.get(config.joystickName).getComponents().length <= config.buttonIndex) {
                                        iterator.remove();
                                        InterfaceManager.coreInterface.logError("Removed classic joystick with too low count.  Had " + classicJoystickMap.get(config.joystickName).getComponents().length + " requested " + config.buttonIndex);
                                    }
                                }
                            } else {
                                if (joystickMap.containsKey(config.joystickName)) {
                                    if (control.isAxis) {
                                        if (joystickMap.get(config.joystickName).getAxisCount() <= config.buttonIndex) {
                                            iterator.remove();
                                            InterfaceManager.coreInterface.logError("Removed joystick with too low axis count.  Had " + joystickMap.get(config.joystickName).getAxisCount() + " requested " + config.buttonIndex);
                                        }
                                    } else {
                                        if (joystickMap.get(config.joystickName).getButtonCount() <= config.buttonIndex - joystickAxisCountMap.get(config.joystickName)) {
                                            iterator.remove();
                                            InterfaceManager.coreInterface.logError("Removed joystick with too low button count.  Had " + joystickMap.get(config.joystickName).getButtonCount() + " requested " + (config.buttonIndex - joystickAxisCountMap.get(config.joystickName)));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            //Invalid control.
                            iterator.remove();
                        }
                    }

                    joystickBlocked = false;
                } catch (Exception e) {
                    e.printStackTrace();
                    InterfaceManager.coreInterface.logError(e.getMessage());
                    for (StackTraceElement s : e.getStackTrace()) {
                        InterfaceManager.coreInterface.logError(s.toString());
                    }
                }
                runningJoystickThread = false;
            });
            joystickThread.start();
        }
    }

    @Override
    public String getNameForKeyCode(int keyCode) {
        return Keyboard.getKeyName(keyCode);
    }

    @Override
    public int getKeyCodeForName(String name) {
        return Keyboard.getKeyIndex(name);
    }

    @Override
    public boolean isKeyPressed(int keyCode) {
        return Keyboard.isKeyDown(keyCode);
    }

    @Override
    public void setGUIControls(boolean enabled) {
        Keyboard.enableRepeatEvents(enabled);
        if (enabled) {
            leftMouseButtonDown = false;
            rightMouseButtonDown = false;
        }
    }

    @Override
    public boolean isJoystickSupportEnabled() {
        return joystickEnabled;
    }

    @Override
    public boolean isJoystickSupportBlocked() {
        return joystickBlocked;
    }

    @Override
    public boolean isJoystickPresent(String joystickName) {
        return runningClassicMode ? classicJoystickMap.containsKey(joystickName) : joystickMap.containsKey(joystickName);
    }

    @Override
    public List<String> getAllJoystickNames() {
        return new ArrayList<>(runningClassicMode ? classicJoystickMap.keySet() : joystickMap.keySet());
    }

    @Override
    public int getJoystickComponentCount(String joystickName) {
        return runningClassicMode ? classicJoystickMap.get(joystickName).getComponents().length : joystickMap.get(joystickName).getAxisCount() + joystickMap.get(joystickName).getButtonCount();
    }

    @Override
    public String getJoystickComponentName(String joystickName, int index) {
        return runningClassicMode ? classicJoystickMap.get(joystickName).getComponents()[index].getName() : (isJoystickComponentAxis(joystickName, index) ? joystickMap.get(joystickName).getAxisName(index) : joystickMap.get(joystickName).getButtonName(index - joystickAxisCountMap.get(joystickName)));
    }

    @Override
    public boolean isJoystickComponentAxis(String joystickName, int index) {
        return runningClassicMode ? classicJoystickMap.get(joystickName).getComponents()[index].isAnalog() : joystickMap.get(joystickName).getAxisCount() > index;
    }

    @Override
    public float getJoystickAxisValue(String joystickName, int index) {
        //Check to make sure this control is operational before testing.  It could have been removed from a prior game.
        if (runningClassicMode) {
            if (classicJoystickMap.containsKey(joystickName)) {
                classicJoystickMap.get(joystickName).poll();
                return classicJoystickMap.get(joystickName).getComponents()[index].getPollData();
            } else {
                return 0;
            }
        } else {
            //Make sure we're not calling this on non-axis.
            if (joystickMap.containsKey(joystickName)) {
                if (isJoystickComponentAxis(joystickName, index)) {
                    //lwjgl might add a default DeadZone for input so just disable it before using
                    joystickMap.get(joystickName).setDeadZone(index, 0);
                    joystickMap.get(joystickName).poll();
                    return joystickMap.get(joystickName).getAxisValue(index);
                } else {
                    return getJoystickButtonValue(joystickName, index) ? 1 : 0;
                }
            } else {
                return 0;
            }
        }
    }

    @Override
    public boolean getJoystickButtonValue(String joystickName, int index) {
        //Check to make sure this control is operational before testing.  It could have been removed from a prior game.
        if (runningClassicMode) {
            if (classicJoystickMap.containsKey(joystickName)) {
                classicJoystickMap.get(joystickName).poll();
                return classicJoystickMap.get(joystickName).getComponents()[index].getPollData() > 0;
            } else {
                return false;
            }
        } else {
            if (joystickMap.containsKey(joystickName)) {
                joystickMap.get(joystickName).poll();
                return joystickMap.get(joystickName).isButtonPressed(index - joystickAxisCountMap.get(joystickName));
            } else {
                return false;
            }
        }
    }

    @Override
    public int getTrackedMouseWheel() {
        return Mouse.hasWheel() ? Mouse.getDWheel() : 0;
    }

    @Override
    public boolean isLeftMouseButtonDown() {
        return betterCombatDetected ? leftMouseButtonDown : Minecraft.getMinecraft().gameSettings.keyBindAttack.isKeyDown();
    }

    @Override
    public boolean isRightMouseButtonDown() {
        return betterCombatDetected ? rightMouseButtonDown : Minecraft.getMinecraft().gameSettings.keyBindUseItem.isKeyDown();
    }
}
