package com.core.attribute.tacz.client;

import com.core.attribute.tacz.data.VanillaHideFlag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Hide-flags editor screen, restyled with the modern glass theme while keeping
 * the exact toggle semantics of the original screen.
 */
public class HideFlagsEditorScreen extends Screen {

    private final AttributeEditorScreen parent;
    private final ItemStack previewStack;
    private int hideFlags;
    private boolean hideMainHandAttributes;
    private boolean closing;

    private long openTime = System.currentTimeMillis();

    public HideFlagsEditorScreen(AttributeEditorScreen parent, ItemStack previewStack, int hideFlags, boolean hideMainHandAttributes) {
        super(Component.literal("隐藏标签"));
        this.parent = parent;
        this.previewStack = previewStack.copy();
        this.hideFlags = hideFlags;
        this.hideMainHandAttributes = hideMainHandAttributes;
    }

    // ---- layout --------------------------------------------------------

    private int panelW() {
        return Math.min(640, this.width - 40);
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return 20;
    }

    private int panelH() {
        return this.height - 40;
    }

    private int gridY() {
        return panelY() + 122;
    }

    private int cardH() {
        return this.panelH() >= 400 ? 40 : 34;
    }

    private int colGap() {
        return 12;
    }

    private int cardW() {
        return (panelW() - 36 - colGap()) / 2;
    }

    private int rowGap() {
        int bottom = bottomY();
        int avail = bottom - gridY() - 50;
        int gap = Math.max(34, Math.min(48, avail / 5));
        return gap;
    }

    private int mainHandY() {
        return gridY() + 4 * rowGap() + 4;
    }

    private int bottomY() {
        return panelY() + panelH() - 42;
    }

    // ---- widget building ----------------------------------------------

    @Override
    protected void init() {
        int cardW = cardW();
        int cardH = cardH();
        int rowGap = rowGap();
        VanillaHideFlag[] flags = VanillaHideFlag.values();
        for (int i = 0; i < flags.length; i++) {
            VanillaHideFlag flag = flags[i];
            int column = i % 2;
            int row = i / 2;
            int x = panelX() + 18 + column * (cardW + colGap());
            int y = gridY() + row * rowGap;
            boolean hidden = this.isHidden(flag);
            this.addRenderableWidget(new PurpleButton(x, y, cardW, cardH,
                    Component.literal(flag.displayName() + (hidden ? " · 隐藏" : " · 显示")),
                    b -> this.toggle(flag),
                    hidden ? 0xFFFF7B7B : 0xFF7BFF9B, PurpleButton.Style.GLASS));
        }
        this.addRenderableWidget(new PurpleButton(panelX() + 18, mainHandY(), panelW() - 36, cardH(),
                Component.literal("主手属性" + (this.hideMainHandAttributes ? " · 隐藏" : " · 显示")),
                b -> this.toggleMainHandAttributes(),
                this.hideMainHandAttributes ? 0xFFFF7B7B : 0xFF7BFF9B, PurpleButton.Style.GLASS));
        this.addRenderableWidget(new PurpleButton(panelX() + 18, bottomY(), 84, 26, Component.literal("返回"),
                b -> this.closeEditor(), UiKit.TEXT_PRIMARY, PurpleButton.Style.PRIMARY));
    }

    // ---- behaviour (unchanged) ---------------------------------------

    private void toggle(VanillaHideFlag flag) {
        this.hideFlags ^= flag.mask();
        this.rebuildFlagWidgets();
    }

    private void toggleMainHandAttributes() {
        this.hideMainHandAttributes = !this.hideMainHandAttributes;
        this.rebuildFlagWidgets();
    }

    private void rebuildFlagWidgets() {
        this.clearWidgets();
        this.init();
    }

    private boolean isHidden(VanillaHideFlag flag) {
        return (this.hideFlags & flag.mask()) != 0;
    }

    private void closeEditor() {
        if (this.closing) {
            return;
        }
        this.closing = true;
        this.parent.updateHideSettings(this.hideFlags, this.hideMainHandAttributes);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        this.closeEditor();
    }

    // ---- rendering -----------------------------------------------------

    private float openProgress() {
        long elapsed = System.currentTimeMillis() - this.openTime;
        return UiKit.easeOut(Math.min(1f, elapsed / 220f));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float p = this.openProgress();
        graphics.fill(0, 0, this.width, this.height, UiKit.scaleAlpha(UiKit.SCRIM, p));
        graphics.pose().pushPose();
        float scale = 0.985f + 0.015f * p;
        graphics.pose().translate(this.width / 2f, this.height / 2f, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-this.width / 2f, -this.height / 2f, 0);
        graphics.setColor(1f, 1f, 1f, p);

        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelW();
        int panelH = panelH();
        UiKit.glassPanel(graphics, panelX, panelY, panelW, panelH, 12);

        // header
        UiKit.drawScaled(graphics, this.font, "隐藏标签", panelX + 20, panelY + 16, 1.4f, UiKit.TEXT_PRIMARY);
        UiKit.drawShadowed(graphics, this.font, "选中的区块不会出现在物品提示中", panelX + 20, panelY + 38, UiKit.TEXT_MUTED);

        // item preview
        int iconCx = panelX + panelW / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(iconCx - 20, panelY + 58, 0);
        graphics.pose().scale(2.5f, 2.5f, 1f);
        graphics.renderItem(this.previewStack, 0, 0);
        graphics.pose().popPose();
        String itemName = UiKit.trim(this.font, this.previewStack.getHoverName().getString(), panelW - 40);
        UiKit.drawScaledCentered(graphics, this.font, itemName, iconCx, panelY + 104, 1f, UiKit.TEXT_PRIMARY);

        // status dots on flag cards
        VanillaHideFlag[] flags = VanillaHideFlag.values();
        int cardW = cardW();
        int cardH = cardH();
        int rowGap = rowGap();
        for (int i = 0; i < flags.length; i++) {
            int column = i % 2;
            int row = i / 2;
            int x = panelX + 18 + column * (cardW + colGap());
            int y = gridY() + row * rowGap;
            boolean hidden = this.isHidden(flags[i]);
            UiKit.fillRounded(graphics, x + 8, y + cardH / 2 - 3, 6, 6, 3, hidden ? UiKit.DANGER : UiKit.SUCCESS);
        }
        UiKit.fillRounded(graphics, panelX + 26, mainHandY() + cardH() / 2 - 3, 6, 6, 3,
                this.hideMainHandAttributes ? UiKit.DANGER : UiKit.SUCCESS);

        // footer counters
        int hiddenCount = Integer.bitCount(this.hideFlags & 0xFF) + (this.hideMainHandAttributes ? 1 : 0);
        String counter = "已隐藏 " + hiddenCount + " / 9 项";
        graphics.drawString(this.font, counter, panelX + panelW - 20 - this.font.width(counter), bottomY() + 9,
                hiddenCount > 0 ? UiKit.ACCENT_BRIGHT : UiKit.TEXT_MUTED, false);

        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}