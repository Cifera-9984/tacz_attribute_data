package com.core.attribute.tacz.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Themed rounded button used across the editor screens. Keeps the historical
 * class name/constructors but renders a modern gradient glass style with a
 * smooth hover transition.
 */
public class PurpleButton extends Button {

    public enum Style {
        /** Neutral dark glass button (default). */
        GLASS,
        /** Purple gradient call-to-action button. */
        PRIMARY,
        /** Red gradient destructive button. */
        DANGER,
        /** Border-only ghost button. */
        GHOST
    }

    private final int textColor;
    private final Style style;
    private float hoverProgress;
    private long lastNanos;

    public PurpleButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        this(x, y, width, height, message, onPress, UiKit.TEXT_PRIMARY, Style.GLASS);
    }

    public PurpleButton(int x, int y, int width, int height, Component message, Button.OnPress onPress, int textColor) {
        this(x, y, width, height, message, onPress, textColor, Style.GLASS);
    }

    public PurpleButton(int x, int y, int width, int height, Component message, Button.OnPress onPress, int textColor, Style style) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
        this.textColor = textColor;
        this.style = style;
    }

    /** Convenience factory matching the legacy dark-purple label look. */
    public static PurpleButton legacy(int x, int y, int width, int height, String text, Button.OnPress onPress, int textColor) {
        return new PurpleButton(x, y, width, height,
                Component.literal(text).withStyle(ChatFormatting.DARK_PURPLE), onPress, textColor, Style.GLASS);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.active && this.isHoveredOrFocused();
        float dt = UiKit.frameDelta(this.lastNanos);
        this.lastNanos = System.nanoTime();
        this.hoverProgress = UiKit.approach(this.hoverProgress, hovered ? 1f : 0f, dt, 14f);

        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        int radius = Math.min(8, h / 2);
        float hover = this.hoverProgress;
        float dim = this.active ? 1f : 0.45f;

        int bgTop;
        int bgBottom;
        int border;
        int glow = 0;
        switch (this.style) {
            case PRIMARY -> {
                bgTop = UiKit.lerpColor(0xFF7C3AED, 0xFF9F67FF, hover);
                bgBottom = UiKit.lerpColor(0xFF5B21B6, 0xFF7C3AED, hover);
                border = UiKit.lerpColor(0x66C9A8FF, 0x99E0C8FF, hover);
                glow = UiKit.lerpColor(0x00000000, 0x369D4EDD, hover);
            }
            case DANGER -> {
                bgTop = UiKit.lerpColor(0xFFB91C1C, 0xFFDC2626, hover);
                bgBottom = UiKit.lerpColor(0xFF7F1D1D, 0xFFB91C1C, hover);
                border = UiKit.lerpColor(0x55FFBBBB, 0x88FFD0D0, hover);
                glow = UiKit.lerpColor(0x00000000, 0x33EF4444, hover);
            }
            case GHOST -> {
                bgTop = UiKit.lerpColor(0x20101018, 0x40202030, hover);
                bgBottom = UiKit.lerpColor(0x180C0C12, 0x38202030, hover);
                border = UiKit.lerpColor(0x28FFFFFF, 0x50FFFFFF, hover);
            }
            default -> {
                bgTop = UiKit.lerpColor(0xC01C1C28, 0xD2343450, hover);
                bgBottom = UiKit.lerpColor(0xC0141420, 0xD2282840, hover);
                border = UiKit.lerpColor(0x32FFFFFF, 0x66FFFFFF, hover);
            }
        }

        // outer glow on hover
        if (glow != 0 && (glow >>> 24) > 0) {
            UiKit.fillRounded(graphics, x - 1, y - 1, w + 2, h + 2, radius + 1, glow);
            UiKit.fillRounded(graphics, x - 2, y - 2, w + 4, h + 4, radius + 2, UiKit.scaleAlpha(glow, 0.4f));
        }
        // body + border (the drop shadow is baked into the border colour to save draw calls)
        UiKit.fillRoundedVGradient(graphics, x, y, w, h, radius,
                UiKit.scaleAlpha(bgTop, dim), UiKit.scaleAlpha(bgBottom, dim));
        UiKit.strokeRounded(graphics, x, y, w, h, radius, UiKit.scaleAlpha(border, dim));
        if (h > 5) {
            graphics.fill(x + radius, y + 1, x + w - radius, y + 2, UiKit.scaleAlpha(0x28FFFFFF, dim));
        }

        // label
        int color = this.active ? UiKit.lerpColor(this.textColor, 0xFFFFFFFF, hover * 0.5f) : 0x70FFFFFF;
        int textY = y + (h - 8) / 2;
        UiKit.drawShadowedCentered(graphics, Minecraft.getInstance().font, this.getMessage(), x + w / 2, textY, color);
    }
}