package com.core.attribute.tacz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * {@link EditBox} that nudges its rendered content down by a fraction of a pixel.
 *
 * <p>Vanilla centres a bare 8px glyph cell in the box, which reads a little high inside the
 * editor's taller rounded backdrops. Shifting the render pose keeps the caret, selection
 * and horizontal scroll perfectly in sync - unlike shifting the widget itself, which would
 * also move the click area.</p>
 */
public class GlassEditBox extends EditBox {

    /** Vertical nudge applied to everything the box draws. */
    private static final float TEXT_NUDGE = 1.0f;

    public GlassEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, TEXT_NUDGE, 0.0f);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }
}
