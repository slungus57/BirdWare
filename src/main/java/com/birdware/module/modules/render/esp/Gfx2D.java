package com.birdware.module.modules.render.esp;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

/**
 * Small allocation-free drawing helpers on top of the deferred {@link GuiGraphics} used by the render modules' 2D
 * overlays (2D boxes, bars, labels). Shapes are built from non-overlapping {@code fill} calls so translucent colors
 * blend evenly.
 */
public final class Gfx2D {
	private Gfx2D() {
	}

	/** Filled rectangle; float edges are rounded to the grid of the current pose. */
	public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
		if ((argb >>> 24) == 0) return;
		int ix0 = Math.round(x0);
		int iy0 = Math.round(y0);
		int ix1 = Math.round(x1);
		int iy1 = Math.round(y1);
		if (ix1 <= ix0 || iy1 <= iy0) return;
		g.fill(ix0, iy0, ix1, iy1, argb);
	}

	/** Hollow rectangle of thickness {@code t}, drawn as four non-overlapping strips (outer edge at the given bounds). */
	public static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int t, int argb) {
		if ((argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) return;
		if (x1 - x0 <= 2 * t || y1 - y0 <= 2 * t) {
			g.fill(x0, y0, x1, y1, argb);
			return;
		}
		g.fill(x0, y0, x1, y0 + t, argb);
		g.fill(x0, y1 - t, x1, y1, argb);
		g.fill(x0, y0 + t, x0 + t, y1 - t, argb);
		g.fill(x1 - t, y0 + t, x1, y1 - t, argb);
	}

	/**
	 * Rectangle with rounded corners. Each corner row is inset by the circle's coverage so the shape stays a single
	 * layer (no double blending of translucent backgrounds).
	 */
	public static void roundedRect(GuiGraphics g, int x0, int y0, int x1, int y1, int radius, int argb) {
		int w = x1 - x0;
		int h = y1 - y0;
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
		int r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x0, y0, x1, y1, argb);
			return;
		}
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, argb);
			g.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, argb);
		}
		g.fill(x0, y0 + r, x1, y1 - r, argb);
	}

	/**
	 * Vertical bar (health, armor) filled from the bottom: dark background with a 1 unit border and a colored part
	 * proportional to {@code fraction}.
	 */
	public static void verticalBar(GuiGraphics g, float x0, float y0, float x1, float y1, float fraction, int argb, int background) {
		rect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, background);
		float f = Math.max(0f, Math.min(1f, fraction));
		rect(g, x0, y1 - (y1 - y0) * f, x1, y1, argb);
	}

	/**
	 * Text centered on {@code centerX} with its top at {@code topY}, scaled around that anchor, optionally on a
	 * padded background panel.
	 */
	public static void label(GuiGraphics g, Font font, String text, float centerX, float topY, float scale,
							 int textArgb, int backgroundArgb, boolean shadow) {
		if (text == null || text.isEmpty()) return;
		int width = font.width(text);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(centerX, topY);
		pose.scale(scale, scale);
		int x = -width / 2;
		if ((backgroundArgb >>> 24) != 0) g.fill(x - 2, -2, x + width + 2, font.lineHeight, backgroundArgb);
		g.drawString(font, text, x, 0, textArgb, shadow);
		pose.popMatrix();
	}

	/** Forces a visible alpha so text is never silently skipped (GuiGraphics ignores alpha 0 text). */
	public static int textColor(int argb) {
		return (argb >>> 24) == 0 ? argb | 0xFF000000 : argb;
	}
}
