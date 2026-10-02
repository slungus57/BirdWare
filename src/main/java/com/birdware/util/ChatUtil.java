package com.birdware.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Client-side chat output with the BirdWare prefix. Safe to call from any thread. */
public final class ChatUtil {
	public static final int PREFIX_COLOR = 0x4FC3F7;
	private static final Minecraft mc = Minecraft.getInstance();

	private ChatUtil() {
	}

	public static MutableComponent prefix() {
		return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
			.append(Component.literal("BirdWare").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(PREFIX_COLOR)).withBold(true)))
			.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Sends a raw component with the prefix. */
	public static void send(Component message) {
		Component full = prefix().append(message);
		if (mc.isSameThread()) addToChat(full);
		else mc.execute(() -> addToChat(full));
	}

	private static void addToChat(Component component) {
		if (mc.gui != null) mc.gui.getChat().addMessage(component);
	}

	public static void info(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.GRAY));
	}

	public static void success(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.GREEN));
	}

	public static void warning(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.YELLOW));
	}

	public static void error(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.RED));
	}

	/** Colored text helper: {@code ChatUtil.colored("text", 0xFF5555)}. */
	public static MutableComponent colored(String text, int rgb) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)));
	}
}
