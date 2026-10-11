package org.webbeans.actions;

import org.openide.util.Lookup;
import org.webbeans.ui.UI;

/** Static helpers mirroring NetBeans' {@code org.openide.util.Utilities} action-related part. */
public final class Utilities {

    private Utilities() {
    }

    /**
     * The global context of the current UI: the activated window's lookup when the window system is installed
     * (always reflecting activation changes), else the UI's own lookup; {@link Lookup#EMPTY} outside a UI task.
     */
    public static Lookup actionsGlobalContext() {
        UI ui = UI.current();
        if (ui == null) {
            return Lookup.EMPTY;
        }
        org.webbeans.windows.WindowManager wm = ui.getLookup().lookup(org.webbeans.windows.WindowManager.class);
        return wm != null ? wm.getGlobalContext() : ui.getLookup();
    }

    /** Same, for an explicit UI. */
    public static Lookup actionsGlobalContext(UI ui) {
        org.webbeans.windows.WindowManager wm = ui.getLookup().lookup(org.webbeans.windows.WindowManager.class);
        return wm != null ? wm.getGlobalContext() : ui.getLookup();
    }

    /**
     * Converts a NetBeans key string to the client syntax used by {@link UI#bindShortcut}: modifiers before the
     * dash ({@code D} = Ctrl, or Cmd on macOS; {@code C} = Ctrl; {@code O} = Alt; {@code S} = Shift; {@code M} =
     * Meta → Ctrl), then the key ({@code S}, {@code F4}, {@code ENTER}, {@code DELETE}, {@code UP}, ...).
     * Returns null for an unparsable string.
     */
    public static String keyToClient(String nbKey) {
        if (nbKey == null || nbKey.isEmpty()) {
            return null;
        }
        String mods = "";
        String key = nbKey;
        int dash = nbKey.lastIndexOf('-');
        if (dash > 0) {
            mods = nbKey.substring(0, dash);
            key = nbKey.substring(dash + 1);
        } else if (dash == 0) {
            key = nbKey.substring(1); // "-" alone or a leading dash: the key is the rest
        }
        if (key.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean ctrl = mods.indexOf('D') >= 0 || mods.indexOf('C') >= 0 || mods.indexOf('M') >= 0;
        boolean alt = mods.indexOf('O') >= 0 || mods.indexOf('A') >= 0;
        boolean shift = mods.indexOf('S') >= 0;
        if (ctrl) {
            sb.append("Ctrl+");
        }
        if (alt) {
            sb.append("Alt+");
        }
        if (shift) {
            sb.append("Shift+");
        }
        sb.append(clientKey(key));
        return sb.toString();
    }

    private static String clientKey(String k) {
        String u = k.toUpperCase(java.util.Locale.ROOT);
        switch (u) {
            case "ENTER": return "Enter";
            case "ESCAPE": return "Escape";
            case "TAB": return "Tab";
            case "SPACE": return " ";
            case "BACK_SPACE": case "BACKSPACE": return "Backspace";
            case "DELETE": return "Delete";
            case "INSERT": return "Insert";
            case "HOME": return "Home";
            case "END": return "End";
            case "PAGE_UP": return "PageUp";
            case "PAGE_DOWN": return "PageDown";
            case "UP": return "ArrowUp";
            case "DOWN": return "ArrowDown";
            case "LEFT": return "ArrowLeft";
            case "RIGHT": return "ArrowRight";
            case "PERIOD": return ".";
            case "COMMA": return ",";
            case "MINUS": return "-";
            case "PLUS": return "+";
            case "EQUALS": return "=";
            case "SLASH": return "/";
            default:
                return u.length() == 1 ? u : u.matches("F\\d{1,2}") ? u : k;
        }
    }

    /** Human-readable form of a client key for menus ({@code Ctrl+Shift+S}); {@code " "} becomes {@code Space}. */
    public static String keyToDisplay(String clientKey) {
        if (clientKey == null) {
            return null;
        }
        return clientKey.endsWith("+ ") ? clientKey.substring(0, clientKey.length() - 1) + "Space"
                : clientKey.equals(" ") ? "Space" : clientKey;
    }
}
