package minecrafttransportsimulator.mcinterface;

import java.util.List;

import minecrafttransportsimulator.systems.ControlSystem;

/**
 * Interface for MC input classes.  Used to query various input settings
 * for the {@link ControlSystem}.  Note that {@link #initJoysticks()} runs
 * in a thread.  As such do NOT call any joystick methods except {@link #isJoystickSupportEnabled()}
 * until it returns true.  Once it does, joysicks may be used.  If it does not, it means joysick
 * support is not enabled.  Note that for component indexes, axis always come first, then buttons, but
 * indexes are not re-used.  Therefore, the first button index will vary depending on how many axis
 * are present on any given joystick.
 *
 * @author don_bruce
 */
public interface IInterfaceInput {

    /**
     * Returns a unique ID for the keyset keybinding.  Different versions of the game have different
     * sets, and if we need to make sure to wipe the config if the sets are different, otherwise we
     * could send obsolete or incorrect keycodes to the input system.
     */
    int getKeysetID();

    /**
     * Called to set the master config key.  This is used by MC to allow us to use MC-controls to open
     * the config menu.  Done here as players should at least see something in the controls menu to
     * cause them to open the config menu for the actual control configuration.
     */
    void initConfigKey();

    /**
     * Tries to populate all joysticks into the map.  Called automatically on first key-press seen on the keyboard as we can be
     * assured the game is running and the configs are loaded by that time.  May be called manually at other times when
     * the joysticks mapped needs to be refreshed.
     */
    void initJoysticks();

    /**
     * Returns the human-readable name for a given integer keycode as defined by the current
     * input class.  Integer value and key name may change between versions!
     */
    String getNameForKeyCode(int keyCode);

    /**
     * Returns the integer keycode for a given human-readable name.
     * Integer value and key name may change between versions!
     */
    int getKeyCodeForName(String name);

    /**
     * Returns true if the given key is currently pressed.
     */
    boolean isKeyPressed(int keyCode);

    /**
     * Tells the input interface to act as if a player-capturing GUI is open.
     * This should always be set when opening a GUI that handles keypresses, 
     * and un-set when that GUI closes.
     */
    void setGUIControls(boolean enabled);

    /**
     * Returns true if joystick support is enabled (found at least 1 joystick).
     */
    boolean isJoystickSupportEnabled();

    /**
     * Returns true if joystick support is blocked.  This happens if the joysick support
     * wasn't able to be checked, either due to a bad driver or locked-up thread.
     */
    boolean isJoystickSupportBlocked();

    /**
     * Returns true if the passed-in joystick is present.
     * Can be used to check if a joystick is plugged-in before polling, and if it isn't
     * fallback logic for keyboard controls can be used.  Note that if joysticks are inhibited,
     * this call will always return false, even if the passed-in joystick exists.
     */
    boolean isJoystickPresent(String joystickName);

    /**
     * Returns a list of all joysticks currently present on the system.
     */
    List<String> getAllJoystickNames();

    /**
     * Returns the number of axis and buttons the passed-in joystick has.
     * Axis will always come before buttons.
     */
    int getJoystickComponentCount(String joystickName);

    /**
     * Returns the name of the passed-in component.
     */
    String getJoystickComponentName(String joystickName, int index);

    /**
     * Returns true if the component at the passed-in index is an axis.
     */
    boolean isJoystickComponentAxis(String joystickName, int index);

    /**
     * Returns the current value of the joystick axis.
     */
    float getJoystickAxisValue(String joystickName, int index);

    /**
     * Returns the current button-state for the joystick axis.  Note that this is used
     * for both analog axis, and fake-digital buttons like Xbox D-pads.
     */
    boolean getJoystickButtonValue(String joystickName, int index);

    /**
     * Returns the current  mouse scroll wheel position, if one exists.
     * Note that this method can only get the delta the mouse wheel, not the absolute
     * change, so unless you call this every tick you will get bad data!
     */
    int getTrackedMouseWheel();

    /**
     * Returns true if the left mouse-button is down.
     */
    boolean isLeftMouseButtonDown();

    /**
     * Returns true if the right mouse-button is down.
     */
    boolean isRightMouseButtonDown();
}
