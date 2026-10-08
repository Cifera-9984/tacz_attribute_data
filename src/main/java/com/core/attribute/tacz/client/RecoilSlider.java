package com.core.attribute.tacz.client;

import com.core.attribute.tacz.data.GunOverrides;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Glass-styled slider for the gun's base recoil multiplier, snapped to
 * {@link GunOverrides#RECOIL_STEP}. Hand drawn (track + fill + knob + value pill) so it
 * matches the rest of the editor instead of the vanilla slider texture.
 */
public class RecoilSlider extends AbstractWidget {

    public interface OnChange {
        void accept(double value);
    }

    private final OnChange onChange;
    private double value;
    private float hover;
    private boolean dragging;
    private long lastNanos;

    public RecoilSlider(int x, int y, int width, int height, double value, OnChange onChange) {
        super(x, y, width, height, Component.empty());
        this.onChange = onChange;
        this.value = clamp(value);
    }

    public double value() {
        return this.value;
    }

    public void setValue(double value) {
        this.value = clamp(value);
    }

    private double clamp(double raw) {
        double snapped = Math.round(raw / GunOverrides.RECOIL_STEP) * GunOverrides.RECOIL_STEP;
        return Math.max(GunOverrides.RECOIL_MIN, Math.min(GunOverrides.RECOIL_MAX, snapped));
    }

    private double ratio() {
        return (this.value - GunOverrides.RECOIL_MIN) / (GunOverrides.RECOIL_MAX - GunOverrides.RECOIL_MIN);
    }

    private void updateFromMouse(double mouseX) {
        double t = (mouseX - this.getX()) / Math.max(1.0, this.getWidth());
        double next = GunOverrides.RECOIL_MIN + t * (GunOverrides.RECOIL_MAX - GunOverrides.RECOIL_MIN);
        double clamped = this.clamp(next);
        if (Math.abs(clamped - this.value) < 0.0001) {
            return;
        }
        this.value = clamped;
        this.onChange.accept(this.value);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float dt = UiKit.frameDelta(this.lastNanos);
        this.lastNanos = System.nanoTime();
        boolean active = this.dragging || this.isHoveredOrFocused();
        this.hover = UiKit.approach(this.hover, active ? 1f : 0f, dt, 14f);

        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        int trackH = 6;
        int trackY = y + (h - trackH) / 2;

        UiKit.fillRounded(graphics, x, trackY, w, trackH, 3, 0x66000000);
        UiKit.strokeRounded(graphics, x, trackY, w, trackH, 3, 0x2AFFFFFF);
        int fillW = (int) Math.round(w * this.ratio());
        if (fillW > 0) {
            UiKit.fillRounded(graphics, x, trackY, fillW, trackH, 3,
                    UiKit.lerpColor(UiKit.ACCENT, UiKit.ACCENT_BRIGHT, this.hover));
        }

        int knobR = 7;
        int knobX = x + fillW - knobR;
        knobX = Math.max(x - knobR, Math.min(knobX, x + w - knobR));
        int knobY = y + (h - knobR * 2) / 2;
        UiKit.fillRounded(graphics, knobX, knobY, knobR * 2, knobR * 2, knobR, 0xFFF2F2F7);
        UiKit.strokeRounded(graphics, knobX, knobY, knobR * 2, knobR * 2, knobR,
                UiKit.lerpColor(UiKit.CARD_BORDER, UiKit.ACCENT_BRIGHT, this.hover));

        String label = String.format(Locale.ROOT, "%.2fx", this.value);
        boolean modified = Math.abs(this.value - GunOverrides.RECOIL_DEFAULT) > 0.0001;
        int textColor = modified ? 0xFFFFD8A8 : UiKit.TEXT_SECONDARY;
        int pillW = Minecraft.getInstance().font.width(label) + 10;
        int pillX = x + (w - pillW) / 2;
        UiKit.fillRounded(graphics, pillX, y, pillW, h, 6, 0xC8101018);
        UiKit.drawShadowedCentered(graphics, Minecraft.getInstance().font, Component.literal(label),
                x + w / 2, y + (h - 8) / 2, textColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.active && this.visible && this.isMouseOver(mouseX, mouseY)) {
            this.dragging = true;
            this.updateFromMouse(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging) {
            this.updateFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging) {
            this.dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
    }
}
