package com.core.attribute.tacz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared rendering helpers for the modern dark-glass UI: rounded rectangles,
 * gradients, strokes, panels and colour interpolation.
 */
public final class UiKit {

    // ---- theme palette -------------------------------------------------
    public static final int SCRIM = 0xC8080810;
    public static final int PANEL_BG = 0xE6121219;
    public static final int PANEL_BORDER = 0x382F2F44;
    public static final int PANEL_TOP_GLOW = 0x30FFFFFF;

    public static final int CARD_BG = 0xA81A1A24;
    public static final int CARD_BG_HOVER = 0xC2232335;
    public static final int CARD_BORDER = 0x2AFFFFFF;
    public static final int CARD_BORDER_HOVER = 0x50FFFFFF;

    public static final int INPUT_BG = 0xCC0E0E15;
    public static final int INPUT_BORDER = 0x33FFFFFF;
    public static final int INPUT_BORDER_FOCUS = 0xFF9D4EDD;

    public static final int TEXT_PRIMARY = 0xFFF2F2F7;
    public static final int TEXT_SECONDARY = 0xFF9CA3AF;
    public static final int TEXT_MUTED = 0xFF6B7280;

    public static final int ACCENT = 0xFF9D4EDD;
    public static final int ACCENT_DEEP = 0xFF6D28D9;
    public static final int ACCENT_BRIGHT = 0xFFC77DFF;

    public static final int DANGER = 0xFFEF4444;
    public static final int DANGER_DEEP = 0xFF991B1B;
    public static final int SUCCESS = 0xFF22C55E;
    public static final int SUCCESS_DEEP = 0xFF15803D;

    private UiKit() {
    }

    // ---- colour helpers ------------------------------------------------
    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int scaleAlpha(int color, float factor) {
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // ---- shape helpers -------------------------------------------------

    /**
     * Draws a rounded rectangle. Small widgets use one fill per scanline (cheaper than
     * the banded form because the corners dominate), large ones use three solid bands
     * plus corner arcs so a full-height panel does not cost hundreds of fills.
     */
    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        if (h <= 4 * r + 10) {
            for (int dy = 0; dy < h; dy++) {
                int inset = cornerInset(dy, h, r);
                g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
            }
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        g.fill(x + r, y, x + w - r, y + r, color);
        g.fill(x + r, y + h - r, x + w - r, y + h, color);
        for (int dy = 0; dy < r; dy++) {
            int inset = cornerInset(dy, h, r);
            g.fill(x + inset, y + dy, x + r, y + dy + 1, color);
            g.fill(x + w - r, y + dy, x + w - inset, y + dy + 1, color);
            g.fill(x + inset, y + h - dy - 1, x + r, y + h - dy, color);
            g.fill(x + w - r, y + h - dy - 1, x + w - inset, y + h - dy, color);
        }
    }

    /** Horizontal inset applied on scanline {@code dy} of a rounded rectangle. */
    private static int cornerInset(int dy, int h, int r) {
        if (dy < r) {
            double d = r - dy - 0.5;
            return r - (int) Math.round(Math.sqrt(Math.max(0.0, r * r - d * d)));
        }
        if (dy >= h - r) {
            double d = r - (h - dy) + 0.5;
            return r - (int) Math.round(Math.sqrt(Math.max(0.0, r * r - d * d)));
        }
        return 0;
    }

    public static void fillRoundedVGradient(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        if (h <= 4 * r + 10) {
            // Short widget: the corners are most of the height, so scanline filling wins.
            for (int dy = 0; dy < h; dy++) {
                int inset = cornerInset(dy, h, r);
                int color = lerpColor(top, bottom, (float) dy / (float) (h - 1));
                g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
            }
            return;
        }
        // Tall widget: three hardware-interpolated quads plus small corner arcs.
        g.fillGradient(x, y + r, x + w, y + h - r, top, bottom);
        float topBandT = (float) r / (float) (h - 1);
        float bottomBandT = (float) (h - r) / (float) (h - 1);
        g.fillGradient(x + r, y, x + w - r, y + r, top, lerpColor(top, bottom, topBandT));
        g.fillGradient(x + r, y + h - r, x + w - r, y + h, lerpColor(top, bottom, bottomBandT), bottom);
        for (int dy = 0; dy < r; dy++) {
            int inset = cornerInset(dy, h, r);
            int topColor = lerpColor(top, bottom, (float) dy / (float) (h - 1));
            int bottomColor = lerpColor(top, bottom, (float) (h - dy - 1) / (float) (h - 1));
            g.fill(x + inset, y + dy, x + r, y + dy + 1, topColor);
            g.fill(x + w - r, y + dy, x + w - inset, y + dy + 1, topColor);
            g.fill(x + inset, y + h - dy - 1, x + r, y + h - dy, bottomColor);
            g.fill(x + w - r, y + h - dy - 1, x + w - inset, y + h - dy, bottomColor);
        }
    }

    public static void strokeRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y, x + 1, y + h, color);
            g.fill(x + w - 1, y, x + w, y + h, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            int dx = r - (int) Math.round(Math.sqrt(Math.max(0.0, r * r - d * d)));
            g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, color);
            g.fill(x + w - dx - 1, y + dy, x + w - dx, y + dy + 1, color);
            g.fill(x + dx, y + h - dy - 1, x + dx + 1, y + h - dy, color);
            g.fill(x + w - dx - 1, y + h - dy - 1, x + w - dx, y + h - dy, color);
        }
    }

    // ---- composite widgets --------------------------------------------
    /** Dark glass panel with border, top glow and drop shadow. */
    public static void glassPanel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        fillRounded(g, x, y + 3, w, h, radius, 0x50000000);
        fillRoundedVGradient(g, x, y, w, h, radius, withAlpha(PANEL_BG, 0xEE), withAlpha(PANEL_BG, 0xDC));
        strokeRounded(g, x, y, w, h, radius, PANEL_BORDER);
        g.fill(x + radius, y, x + w - radius, y + 1, PANEL_TOP_GLOW);
    }

    /** Standard rounded list-row card; {@code hover} in 0..1 drives the highlight. */
    public static void rowCard(GuiGraphics g, int x, int y, int w, int h, float hover) {
        int bg = lerpColor(CARD_BG, CARD_BG_HOVER, hover);
        int border = lerpColor(CARD_BORDER, CARD_BORDER_HOVER, hover);
        fillRounded(g, x, y, w, h, 8, bg);
        strokeRounded(g, x, y, w, h, 8, border);
    }

    /** Rounded input backdrop drawn underneath a borderless {@code EditBox}. */
    public static void inputBackdrop(GuiGraphics g, int x, int y, int w, int h, boolean focused, float hover) {
        int border = focused ? INPUT_BORDER_FOCUS : lerpColor(INPUT_BORDER, CARD_BORDER_HOVER, hover);
        fillRounded(g, x, y, w, h, 6, INPUT_BG);
        strokeRounded(g, x, y, w, h, 6, border);
    }

    /** Accent bar used at the left edge of attribute rows. */
    public static void accentBar(GuiGraphics g, int x, int y, int h, int color) {
        fillRounded(g, x, y, 3, h, 2, color);
    }

    // ---- text helpers --------------------------------------------------
    public static void drawShadowed(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x + 1, y + 1, 0xA0000000, false);
        g.drawString(font, text, x, y, color, false);
    }

    public static void drawShadowedCentered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        g.drawCenteredString(font, text, cx + 1, y + 1, 0xA0000000);
        g.drawCenteredString(font, text, cx, y, color);
    }

    public static void drawScaled(GuiGraphics g, Font font, String text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        drawShadowed(g, font, text, 0, 0, color);
        g.pose().popPose();
    }

    public static void drawScaledCentered(GuiGraphics g, Font font, String text, int cx, int y, float scale, int color) {
        int w = font.width(text);
        int x = (int) (cx - w * scale / 2f);
        drawScaled(g, font, text, x, y, scale, color);
    }

    /** Truncates a string so it fits into {@code maxWidth} pixels. */
    public static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(sb.toString() + text.charAt(i) + "...") > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + "...";
    }

    /** Smooth ease-out curve for open/close animations. */
    public static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    public static float easeOutBack(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    /**
     * Seconds elapsed since {@code lastNanos}, clamped to a sane range. Returns one
     * 60 Hz frame on the very first call. Pass the previous return source so the same
     * animation speed is produced no matter how many frames per second the game runs
     * (uncapped / high refresh monitors included).
     */
    public static float frameDelta(long lastNanos) {
        if (lastNanos == 0L) {
            return 1f / 60f;
        }
        float dt = (System.nanoTime() - lastNanos) / 1_000_000_000f;
        return Math.max(0.0002f, Math.min(dt, 0.1f));
    }

    /**
     * Frame-rate independent exponential smoothing towards {@code target}.
     *
     * @param dt   elapsed seconds since the previous frame (see {@link #frameDelta})
     * @param rate approach speed in 1/s; higher is snappier
     */
    public static float approach(float current, float target, float dt, float rate) {
        float factor = 1f - (float) Math.exp(-Math.max(0f, dt) * rate);
        float next = current + (target - current) * factor;
        return Math.abs(target - next) < 0.0015f ? target : next;
    }
}