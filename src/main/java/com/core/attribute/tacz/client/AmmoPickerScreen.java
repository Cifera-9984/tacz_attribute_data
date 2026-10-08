package com.core.attribute.tacz.client;

import com.core.attribute.tacz.data.ItemProbe;
import com.tacz.guns.api.TimelessAPI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Ammo picker for the gun card: a searchable, creative-inventory style grid of every
 * TACZ ammo item. Clicking a slot reports the chosen ammo id back to the editor.
 *
 * <p>The grid is drawn and hit-tested manually (like the enchant editor) so hundreds of
 * entries stay cheap and the item icons keep their authentic models.</p>
 */
public class AmmoPickerScreen extends Screen {

    private static final int CELL = 28;
    private static final int GAP = 6;
    private static final int INPUT_H = 20;

    private record Ammo(ItemStack stack, String id, String name) {
    }

    private final AttributeEditorScreen parent;
    private final ItemStack previewStack;
    private final String currentAmmoId;
    private final Consumer<String> onPick;
    private final List<Ammo> all = new ArrayList<>();
    private final List<Ammo> results = new ArrayList<>();

    private EditBox search;
    private String query = "";
    private int scroll;

    private long openTime = System.currentTimeMillis();
    private long lastFrameNanos;
    private float[] cellHover = new float[0];
    private int hoveredIndex = -1;

    public AmmoPickerScreen(AttributeEditorScreen parent, ItemStack previewStack,
                            String currentAmmoId, Consumer<String> onPick) {
        super(Component.literal("选择主弹药"));
        this.parent = parent;
        this.previewStack = previewStack.copy();
        this.currentAmmoId = currentAmmoId;
        this.onPick = onPick;
        this.collectAmmon();
        this.refilter();
    }

    /**
     * TACZ ships one shared ammo item and keeps the ammo type in its NBT, so the picker is
     * built from the ammo ids TACZ actually knows about rather than from the item registry.
     */
    private void collectAmmon() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        try {
            TimelessAPI.getAllCommonAmmoIndex().forEach(entry -> ids.add(entry.getKey()));
            if (ids.isEmpty()) {
                TimelessAPI.getAllClientAmmoIndex().forEach(entry -> ids.add(entry.getKey()));
            }
        } catch (RuntimeException ignored) {
            // Never let a resource-loading hiccup take the picker down.
        }
        List<Ammo> found = new ArrayList<>();
        for (ResourceLocation id : ids) {
            ItemStack stack = ItemProbe.ammoStack(id);
            if (stack.isEmpty()) {
                continue;
            }
            found.add(new Ammo(stack, id.toString(), stack.getHoverName().getString()));
        }
        found.sort(Comparator.comparing(ammo -> ammo.name().toLowerCase(Locale.ROOT)));
        this.all.addAll(found);
    }

    private void refilter() {
        this.results.clear();
        String q = this.query.trim().toLowerCase(Locale.ROOT);
        for (Ammo ammo : this.all) {
            if (ammo.name().toLowerCase(Locale.ROOT).contains(q) || ammo.id().toLowerCase(Locale.ROOT).contains(q)) {
                this.results.add(ammo);
            }
        }
        this.scroll = 0;
    }

    // ---- geometry ------------------------------------------------------

    private int panelW() {
        return Math.min(620, this.width - 30);
    }

    private int panelX() {
        return (this.width - this.panelW()) / 2;
    }

    private int panelY() {
        return 24;
    }

    private int panelH() {
        return this.height - 48;
    }

    private int innerX() {
        return this.panelX() + 16;
    }

    private int innerW() {
        return this.panelW() - 32;
    }

    private int columns() {
        return Math.max(1, (this.innerW() + GAP) / (CELL + GAP));
    }

    private int gridTop() {
        return this.panelY() + 62;
    }

    private int gridBottom() {
        return this.panelY() + this.panelH() - 46;
    }

    private int visibleRows() {
        return Math.max(1, (this.gridBottom() - this.gridTop()) / (CELL + GAP));
    }

    private int maxScroll() {
        int rows = (this.results.size() + this.columns() - 1) / this.columns();
        return Math.max(0, rows - this.visibleRows());
    }

    // ---- widgets -------------------------------------------------------

    @Override
    protected void init() {
        this.openTime = System.currentTimeMillis();
        this.clearWidgets();
        this.setFocused((GuiEventListener) null);

        this.search = new GlassEditBox(this.font, this.innerX() + 6, this.panelY() + 36, this.innerW() - 12, INPUT_H,
                Component.literal("搜索弹药"));
        this.search.setMaxLength(48);
        this.search.setBordered(false);
        this.search.setTextColor(0xFFF2F2F7);
        this.search.setValue(this.query);
        this.search.setHint(Component.literal("搜索弹药名称或 ID...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.search);

        int buttonW = 78;
        this.addRenderableWidget(new PurpleButton(this.panelX() + this.panelW() - 16 - buttonW,
                this.panelY() + this.panelH() - 34, buttonW, 24, Component.literal("完成"),
                b -> this.closeEditor(), UiKit.TEXT_PRIMARY, PurpleButton.Style.PRIMARY));
        this.addRenderableWidget(new PurpleButton(this.panelX() + 16, this.panelY() + this.panelH() - 34,
                buttonW, 24, Component.literal("恢复默认"),
                b -> this.pick(""), UiKit.TEXT_PRIMARY, PurpleButton.Style.DANGER));
    }

    private void pick(String ammoId) {
        this.onPick.accept(ammoId);
        this.closeEditor();
    }

    private void closeEditor() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    // ---- input ---------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int index = this.indexAt(mouseX, mouseY);
        if (index >= 0 && index < this.results.size()) {
            this.pick(this.results.get(index).id());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Grid index under the cursor, or {@code -1} when the point is outside the grid. */
    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < this.innerX() || mouseX > this.innerX() + this.innerW()) {
            return -1;
        }
        if (mouseY < this.gridTop() || mouseY > this.gridBottom()) {
            return -1;
        }
        int col = (int) ((mouseX - this.innerX()) / (CELL + GAP));
        int row = (int) ((mouseY - this.gridTop()) / (CELL + GAP));
        if (col < 0 || col >= this.columns()) {
            return -1;
        }
        return (this.scroll + row) * this.columns() + col;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= this.gridTop() && mouseY <= this.gridBottom()) {
            this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        this.syncQuery();
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        this.syncQuery();
        return handled;
    }

    private void syncQuery() {
        if (this.search == null || !this.search.isFocused()) {
            return;
        }
        String next = this.search.getValue();
        if (next.equals(this.query)) {
            return;
        }
        this.query = next;
        this.refilter();
    }

    // ---- rendering -----------------------------------------------------

    private float openProgress() {
        long elapsed = System.currentTimeMillis() - this.openTime;
        return UiKit.easeOut(Math.min(1f, elapsed / 220f));
    }

    private void updateHover(float dt) {
        int size = this.results.size();
        if (this.cellHover.length != size) {
            float[] resized = new float[size];
            System.arraycopy(this.cellHover, 0, resized, 0, Math.min(this.cellHover.length, size));
            this.cellHover = resized;
        }
        for (int i = 0; i < size; i++) {
            this.cellHover[i] = UiKit.approach(this.cellHover[i], i == this.hoveredIndex ? 1f : 0f, dt, 16f);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float dt = UiKit.frameDelta(this.lastFrameNanos);
        this.lastFrameNanos = System.nanoTime();
        this.hoveredIndex = this.indexAt(mouseX, mouseY);
        if (this.hoveredIndex >= this.results.size()) {
            this.hoveredIndex = -1;
        }
        this.updateHover(dt);
        float p = this.openProgress();

        graphics.fill(0, 0, this.width, this.height, UiKit.scaleAlpha(UiKit.SCRIM, p));
        graphics.pose().pushPose();
        float scale = 0.985f + 0.015f * p;
        graphics.pose().translate(this.width / 2f, this.height / 2f, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-this.width / 2f, -this.height / 2f, 0);
        graphics.setColor(1f, 1f, 1f, p);

        UiKit.glassPanel(graphics, this.panelX(), this.panelY(), this.panelW(), this.panelH(), 12);
        UiKit.drawScaled(graphics, this.font, "选择主弹药", this.panelX() + 18, this.panelY() + 10, 1.4f, UiKit.TEXT_PRIMARY);
        UiKit.drawShadowed(graphics, this.font, "点击弹药设为该枪的主弹药 · 共 " + this.results.size() + " 种",
                this.panelX() + 18, this.panelY() + 27, UiKit.TEXT_MUTED);

        UiKit.inputBackdrop(graphics, this.innerX() + 2, this.panelY() + 36, this.innerW() - 4, INPUT_H,
                this.search != null && this.search.isFocused(), 0f);

        UiKit.accentBar(graphics, this.innerX(), this.gridTop() - 14, 9, UiKit.ACCENT);
        UiKit.drawShadowed(graphics, this.font, "弹药列表", this.innerX() + 12, this.gridTop() - 16, UiKit.TEXT_SECONDARY);
        String current = this.currentAmmoId == null || this.currentAmmoId.isBlank() ? "枪械原弹药" : this.currentAmmoId;
        UiKit.drawShadowed(graphics, this.font, "当前: " + current,
                this.innerX() + this.innerW() - this.font.width("当前: " + current), this.gridTop() - 16, UiKit.ACCENT_BRIGHT);

        graphics.enableScissor(this.panelX(), this.gridTop() - 2, this.panelX() + this.panelW(), this.gridBottom());
        this.renderGrid(graphics);
        graphics.disableScissor();

        if (this.results.isEmpty()) {
            UiKit.drawScaledCentered(graphics, this.font, "没有匹配的弹药",
                    this.panelX() + this.panelW() / 2, this.gridTop() + 8, 1f, UiKit.TEXT_MUTED);
        }

        // Hovered ammo name, shown in a strip just above the footer.
        if (this.hoveredIndex >= 0 && this.hoveredIndex < this.results.size()) {
            Ammo ammo = this.results.get(this.hoveredIndex);
            String label = ammo.name() + "  (" + ammo.id() + ")";
            UiKit.drawShadowedCentered(graphics, this.font,
                    Component.literal(UiKit.trim(this.font, label, this.panelW() - 40)),
                    this.panelX() + this.panelW() / 2, this.gridBottom() + 6, UiKit.TEXT_PRIMARY);
        }

        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderGrid(GuiGraphics graphics) {
        int cols = this.columns();
        int step = CELL + GAP;
        int start = this.scroll * cols;
        int end = Math.min(this.results.size(), start + cols * this.visibleRows());
        for (int i = start; i < end; i++) {
            int rel = i - start;
            int col = rel % cols;
            int row = rel / cols;
            int x = this.innerX() + col * step;
            int y = this.gridTop() + row * step;
            Ammo ammo = this.results.get(i);
            boolean selected = ammo.id().equals(this.currentAmmoId);
            float hover = this.cellHover[i];

            int bg = UiKit.lerpColor(UiKit.scaleAlpha(UiKit.CARD_BG, 0.9f), UiKit.CARD_BG_HOVER, Math.max(hover, selected ? 0.6f : 0f));
            UiKit.fillRounded(graphics, x, y, CELL, CELL, 7, bg);
            UiKit.strokeRounded(graphics, x, y, CELL, CELL, 7,
                    selected ? UiKit.ACCENT_BRIGHT : UiKit.lerpColor(UiKit.CARD_BORDER, UiKit.CARD_BORDER_HOVER, hover));

            graphics.pose().pushPose();
            graphics.pose().translate(x + 6, y + 6, 0);
            graphics.renderItem(ammo.stack(), 0, 0);
            graphics.pose().popPose();

            if (selected) {
                UiKit.fillRounded(graphics, x + CELL - 11, y + 1, 10, 10, 5, UiKit.ACCENT_BRIGHT);
                UiKit.drawShadowedCentered(graphics, this.font, Component.literal("✓"),
                        x + CELL - 6, y + 2, 0xFF101018);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
