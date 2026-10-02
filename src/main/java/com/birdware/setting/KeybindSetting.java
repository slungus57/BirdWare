package com.birdware.setting;

import com.birdware.util.KeyUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** A keyboard key or mouse button. {@link Bind#NONE} means unbound. */
public class KeybindSetting extends Setting<KeybindSetting.Bind> {
	/**
	 * @param code  GLFW key code, or mouse button index when {@code mouse} is true; -1 = unbound
	 * @param mouse whether {@code code} is a mouse button
	 */
	public record Bind(int code, boolean mouse) {
		public static final Bind NONE = new Bind(KeyUtil.NONE, false);

		public static Bind key(int code) {
			return new Bind(code, false);
		}

		public static Bind mouseButton(int button) {
			return new Bind(button, true);
		}

		public boolean isBound() {
			return code != KeyUtil.NONE;
		}

		public boolean matchesKey(int keyCode) {
			return !mouse && code != KeyUtil.NONE && code == keyCode;
		}

		public boolean matchesMouse(int button) {
			return mouse && code == button;
		}

		public String displayName() {
			if (!isBound()) return "None";
			return mouse ? KeyUtil.mouseName(code) : KeyUtil.keyName(code);
		}
	}

	public KeybindSetting(String name, String description, Bind defaultBind) {
		super(name, description, defaultBind);
	}

	public Bind getBind() {
		return value;
	}

	public boolean isBound() {
		return value.isBound();
	}

	public void unbind() {
		set(Bind.NONE);
	}

	@Override
	public String getDisplayValue() {
		return value.displayName();
	}

	@Override
	public JsonElement toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("code", value.code());
		object.addProperty("mouse", value.mouse());
		return object;
	}

	@Override
	public boolean fromJson(JsonElement element) {
		try {
			if (element == null) return false;
			if (element.isJsonPrimitive()) {
				JsonPrimitive primitive = element.getAsJsonPrimitive();
				if (primitive.isNumber()) return set(Bind.key(primitive.getAsInt()));
				return parse(primitive.getAsString());
			}
			if (!element.isJsonObject()) return false;
			JsonObject object = element.getAsJsonObject();
			int code = object.has("code") ? object.get("code").getAsInt() : KeyUtil.NONE;
			boolean mouse = object.has("mouse") && object.get("mouse").getAsBoolean();
			if (code < KeyUtil.NONE || code > 512) return false;
			return set(code == KeyUtil.NONE ? Bind.NONE : new Bind(code, mouse));
		} catch (RuntimeException e) {
			return false;
		}
	}

	@Override
	public boolean parse(String input) {
		Integer code = KeyUtil.parse(input);
		if (code == null) return false;
		if (code == KeyUtil.NONE) return set(Bind.NONE);
		if (KeyUtil.isMouseCode(code)) return set(Bind.mouseButton(KeyUtil.mouseButtonOf(code)));
		return set(Bind.key(code));
	}

	@Override
	public List<String> getSuggestions() {
		return List.of("none", "R", "RShift", "MouseMiddle", "Mouse4");
	}
}
