package com.birdware.util;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Conversion between GLFW key codes / mouse buttons and short human readable names used by binds, the GUI and the
 * {@code .bw bind} command. Names are layout independent for special keys and use GLFW's layout aware names for
 * printable keys when available.
 */
public final class KeyUtil {
	public static final int NONE = -1;

	private static final Map<Integer, String> SPECIAL = new HashMap<>();
	private static final Map<String, Integer> BY_NAME = new HashMap<>();

	static {
		special(GLFW.GLFW_KEY_SPACE, "Space");
		special(GLFW.GLFW_KEY_ESCAPE, "Escape");
		special(GLFW.GLFW_KEY_ENTER, "Enter");
		special(GLFW.GLFW_KEY_TAB, "Tab");
		special(GLFW.GLFW_KEY_BACKSPACE, "Backspace");
		special(GLFW.GLFW_KEY_INSERT, "Insert");
		special(GLFW.GLFW_KEY_DELETE, "Delete");
		special(GLFW.GLFW_KEY_RIGHT, "Right");
		special(GLFW.GLFW_KEY_LEFT, "Left");
		special(GLFW.GLFW_KEY_DOWN, "Down");
		special(GLFW.GLFW_KEY_UP, "Up");
		special(GLFW.GLFW_KEY_PAGE_UP, "PageUp");
		special(GLFW.GLFW_KEY_PAGE_DOWN, "PageDown");
		special(GLFW.GLFW_KEY_HOME, "Home");
		special(GLFW.GLFW_KEY_END, "End");
		special(GLFW.GLFW_KEY_CAPS_LOCK, "CapsLock");
		special(GLFW.GLFW_KEY_SCROLL_LOCK, "ScrollLock");
		special(GLFW.GLFW_KEY_NUM_LOCK, "NumLock");
		special(GLFW.GLFW_KEY_PRINT_SCREEN, "PrintScreen");
		special(GLFW.GLFW_KEY_PAUSE, "Pause");
		special(GLFW.GLFW_KEY_LEFT_SHIFT, "LShift");
		special(GLFW.GLFW_KEY_RIGHT_SHIFT, "RShift");
		special(GLFW.GLFW_KEY_LEFT_CONTROL, "LControl");
		special(GLFW.GLFW_KEY_RIGHT_CONTROL, "RControl");
		special(GLFW.GLFW_KEY_LEFT_ALT, "LAlt");
		special(GLFW.GLFW_KEY_RIGHT_ALT, "RAlt");
		special(GLFW.GLFW_KEY_LEFT_SUPER, "LSuper");
		special(GLFW.GLFW_KEY_RIGHT_SUPER, "RSuper");
		special(GLFW.GLFW_KEY_MENU, "Menu");
		special(GLFW.GLFW_KEY_GRAVE_ACCENT, "Grave");
		special(GLFW.GLFW_KEY_MINUS, "Minus");
		special(GLFW.GLFW_KEY_EQUAL, "Equal");
		special(GLFW.GLFW_KEY_LEFT_BRACKET, "LBracket");
		special(GLFW.GLFW_KEY_RIGHT_BRACKET, "RBracket");
		special(GLFW.GLFW_KEY_BACKSLASH, "Backslash");
		special(GLFW.GLFW_KEY_SEMICOLON, "Semicolon");
		special(GLFW.GLFW_KEY_APOSTROPHE, "Apostrophe");
		special(GLFW.GLFW_KEY_COMMA, "Comma");
		special(GLFW.GLFW_KEY_PERIOD, "Period");
		special(GLFW.GLFW_KEY_SLASH, "Slash");
		special(GLFW.GLFW_KEY_KP_DECIMAL, "NumpadDecimal");
		special(GLFW.GLFW_KEY_KP_DIVIDE, "NumpadDivide");
		special(GLFW.GLFW_KEY_KP_MULTIPLY, "NumpadMultiply");
		special(GLFW.GLFW_KEY_KP_SUBTRACT, "NumpadSubtract");
		special(GLFW.GLFW_KEY_KP_ADD, "NumpadAdd");
		special(GLFW.GLFW_KEY_KP_ENTER, "NumpadEnter");
		special(GLFW.GLFW_KEY_KP_EQUAL, "NumpadEqual");
		for (int i = 0; i <= 9; i++) {
			special(GLFW.GLFW_KEY_KP_0 + i, "Numpad" + i);
			special(GLFW.GLFW_KEY_0 + i, Integer.toString(i));
		}
		for (int i = 1; i <= 25; i++) special(GLFW.GLFW_KEY_F1 + i - 1, "F" + i);
		for (char c = 'A'; c <= 'Z'; c++) special(GLFW.GLFW_KEY_A + (c - 'A'), Character.toString(c));
		// Friendly aliases accepted by the bind command.
		BY_NAME.put("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
		BY_NAME.put("rightshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
		BY_NAME.put("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
		BY_NAME.put("leftshift", GLFW.GLFW_KEY_LEFT_SHIFT);
		BY_NAME.put("rctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
		BY_NAME.put("lctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
		BY_NAME.put("esc", GLFW.GLFW_KEY_ESCAPE);
		BY_NAME.put("return", GLFW.GLFW_KEY_ENTER);
		BY_NAME.put("del", GLFW.GLFW_KEY_DELETE);
		BY_NAME.put("ins", GLFW.GLFW_KEY_INSERT);
	}

	private static void special(int code, String name) {
		SPECIAL.put(code, name);
		BY_NAME.put(name.toLowerCase(Locale.ROOT), code);
	}

	private KeyUtil() {
	}

	/** Name for a keyboard key code. */
	public static String keyName(int keyCode) {
		if (keyCode == NONE) return "None";
		String special = SPECIAL.get(keyCode);
		if (special != null) return special;
		try {
			String glfw = GLFW.glfwGetKeyName(keyCode, 0);
			if (glfw != null && !glfw.isBlank()) return glfw.toUpperCase(Locale.ROOT);
		} catch (Throwable ignored) {
			// GLFW not initialised (e.g. early startup); fall through to numeric name.
		}
		return "Key" + keyCode;
	}

	/** Name for a mouse button index (0 = left). */
	public static String mouseName(int button) {
		return switch (button) {
			case 0 -> "MouseLeft";
			case 1 -> "MouseRight";
			case 2 -> "MouseMiddle";
			default -> "Mouse" + (button + 1);
		};
	}

	/**
	 * Parses a key name. Returns a bind-encoded int: keyboard keys are returned as-is, mouse buttons are returned as
	 * {@code -(button + 100)}; use {@link #isMouseCode(int)} / {@link #mouseButtonOf(int)} to decode. Returns null
	 * when the name is unknown.
	 */
	public static Integer parse(String name) {
		if (name == null) return null;
		String lower = name.trim().toLowerCase(Locale.ROOT);
		if (lower.isEmpty()) return null;
		if (lower.equals("none") || lower.equals("unbind") || lower.equals("unbound")) return NONE;
		switch (lower) {
			case "mouseleft", "lmb", "mouse1" -> {
				return mouseCode(0);
			}
			case "mouseright", "rmb", "mouse2" -> {
				return mouseCode(1);
			}
			case "mousemiddle", "mmb", "mouse3" -> {
				return mouseCode(2);
			}
			default -> {
			}
		}
		if (lower.startsWith("mouse")) {
			try {
				int n = Integer.parseInt(lower.substring(5));
				if (n >= 1 && n <= 8) return mouseCode(n - 1);
			} catch (NumberFormatException ignored) {
			}
		}
		Integer code = BY_NAME.get(lower);
		if (code != null) return code;
		if (lower.length() == 1) {
			// Printable key on a non-US layout: search GLFW names.
			for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
				try {
					String glfw = GLFW.glfwGetKeyName(key, 0);
					if (glfw != null && glfw.equalsIgnoreCase(lower)) return key;
				} catch (Throwable ignored) {
					break;
				}
			}
		}
		if (lower.startsWith("key")) {
			try {
				return Integer.parseInt(lower.substring(3));
			} catch (NumberFormatException ignored) {
			}
		}
		return null;
	}

	public static int mouseCode(int button) {
		return -(button + 100);
	}

	public static boolean isMouseCode(int code) {
		return code <= -100;
	}

	public static int mouseButtonOf(int code) {
		return -code - 100;
	}
}
