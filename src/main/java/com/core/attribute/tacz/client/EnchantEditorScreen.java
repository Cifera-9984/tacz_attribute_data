package com.core.attribute.tacz.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * Dedicated enchantment editor.
 *
 * <p>The enchantment list used to live inside the main editor's accordion, where a few
 * cramped rows had to double as both "search results" and "already applied" - which made
 * it awkward to use. Here the two jobs are split into clear columns: search and click to
 * add on the left, adjust level or remove on the right.</p>
 *
 * <p>Rows are drawn manually and hit-tested from mouse coordinates rather than being made
 * out of widgets, so the screen stays cheap even though there are hundreds of
 * enchantments.</p>
 */
public class EnchantEditorScreen extends Screen {

    private static final int ROW_H = 20;
    private static final int PANEL_MAX_W = 700;
    private static final int INPUT_H = 20;
    private static final int HEADER_H = 30;

    private final AttributeEditorScreen parent;
    private final ItemStack previewStack;
    private final LinkedHashMap<Enchantment, Integer> edits;
    private final List<Enchantment> all = new ArrayList<>();
    private final List<Enchantment> results = new ArrayList<>();
    private final List<Enchantment> addedOrder = new ArrayList<>();
    private final float[] hover = new float[0];

    private EditBox search;
    private String query = "";
    private int resultScroll;
    private int addedScroll;

    private long openTime = System.currentTimeMillis();
    private long lastFrameNanos;
    private float[] rowHover = new float[0];

    public EnchantEditorScreen(AttributeEditorScreen parent, ItemStack previewStack,
                               LinkedHashMap<Enchantment, Integer> edits) {
        super(Component.literal("附魔编辑"));
        this.parent = parent;
        this.previewStack = previewStack.copy();
        this.edits = edits;
        this.all.addAll(ForgeRegistries.ENCHANTMENTS.getValues());
        this.all.sort(Comparator.comparing(e -> e.getFullname(1).getString().toLowerCase(Locale.ROOT)));
        this.refilter();
        this.rebuildAddedOrder();
    }

    // ---- geometry ------------------------------------------------------

    private int panelW() {
        return Math.min(PANEL_MAX_W, this.width - 30);
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return 18;
    }

    private int panelH() {
        return this.height - 36;
    }

    private int gap() {
        return 14;
    }

    private int leftX() {
        return panelX() + 16;
    }

    private int leftW() {
        return (panelW() - 32 - this.gap()) * 6 / 10;
    }

    private int rightX() {
        return this.leftX() + this.leftW() + this.gap();
    }

    private int rightW() {
        return panelX() + panelW() - 16 - this.rightX();
    }

    private int listTop() {
        return panelY() + 58;
    }

    private int listBottom() {
        return panelY() + panelH() - 46;
    }

    private int rowsVisible() {
        return Math.max(1, (this.listBottom() - this.listTop()) / ROW_H);
    }

    private int rowY(int scroll, int index) {
        return this.listTop() + (index - scroll) * ROW_H;
    }

    // ---- data ----------------------------------------------------------

    private void refilter() {
        this.results.clear();
        String q = this.query.trim().toLowerCase(Locale.ROOT);
        for (Enchantment enchantment : this.all) {
            if (enchantment.getFullname(1).getString().toLowerCase(Locale.ROOT).contains(q)) {
                this.results.add(enchantment);
            }
        }
    }

    private void rebuildAddedOrder() {
        this.addedOrder.clear();
        this.addedOrder.addAll(this.edits.keySet());
    }

    // ---- widgets -------------------------------------------------------

    @Override
    protected void init() {
        this.openTime = System.currentTimeMillis();
        this.rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        this.setFocused((GuiEventListener) null);
        int boxX = this.leftX() + 2;
        int boxW = Math.max(80, this.leftW() - 4);
        this.search = new GlassEditBox(this.font, boxX + 6, this.panelY() + HEADER_H + 6, boxW - 12, INPUT_H,
                Component.literal("搜索附魔"));
        this.search.setMaxLength(32);
        this.search.setBordered(false);
        this.search.setTextColor(0xFFF2F2F7);
        this.search.setValue(this.query);
        this.search.setHint(Component.literal("搜索附魔...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.search);

        int buttonW = 78;
        this.addRenderableWidget(new PurpleButton(this.panelX() + panelW() - 16 - buttonW,
                this.panelY() + panelH() - 34, buttonW, 24, Component.literal("完成"),
                b -> this.closeEditor(), UiKit.TEXT_PRIMARY, PurpleButton.Style.PRIMARY));
        this.addRenderableWidget(new PurpleButton(this.panelX() + 16, this.panelY() + panelH() - 34, buttonW, 24,
                Component.literal("清空全部"),
                b -> {
                    this.edits.clear();
                    this.rebuildAddedOrder();
                    this.rebuild();
                }, UiKit.TEXT_PRIMARY, PurpleButton.Style.DANGER));
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
        int visible = this.rowsVisible();
        int row = (int) ((mouseY - this.listTop()) / ROW_H);
        boolean insideList = mouseY >= this.listTop() && mouseY < this.listBottom() && row >= 0 && row < visible;

        if (insideList && mouseX >= this.leftX() && mouseX <= this.leftX() + this.leftW()) {
            int index = this.resultScroll + row;
            if (index >= 0 && index < this.results.size()) {
                Enchantment enchantment = this.results.get(index);
                if (!this.edits.containsKey(enchantment)) {
                    this.edits.put(enchantment, 1);
                    this.rebuildAddedOrder();
                    this.rebuild();
                }
                return true;
            }
        }
        if (insideList && mouseX >= this.rightX() && mouseX <= this.rightX() + this.rightW()) {
            int index = this.addedScroll + row;
            if (index >= 0 && index < this.addedOrder.size()) {
                Enchantment enchantment = this.addedOrder.get(index);
                int x = (int) mouseX;
                if (x >= this.minusX() && x < this.minusX() + 18) {
                    this.bump(enchantment, -1);
                } else if (x >= this.minusX() + 20 && x < this.minusX() + 38) {
                    this.bump(enchantment, 1);
                } else if (x >= this.removeX() && x < this.removeX() + 18) {
                    this.edits.remove(enchantment);
                    this.rebuildAddedOrder();
                    this.rebuild();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void bump(Enchantment enchantment, int delta) {
        Integer current = this.edits.get(enchantment);
        if (current == null) {
            return;
        }
        this.edits.put(enchantment, Math.max(1, Math.min(current + delta, enchantment.getMaxLevel())));
        this.rebuild();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= this.listTop() && mouseY <= this.listBottom()) {
            int step = (int) Math.signum(delta);
            if (mouseX >= this.rightX()) {
                this.addedScroll = this.clamp(this.addedScroll - step, this.addedOrder.size());
            } else if (mouseX >= this.leftX()) {
                this.resultScroll = this.clamp(this.resultScroll - step, this.results.size());
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int clamp(int value, int total) {
        return Math.max(0, Math.min(value, Math.max(0, total - this.rowsVisible())));
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
        this.resultScroll = 0;
    }

    // ---- layout of the per-row action buttons --------------------------

    private int minusX() {
        return this.rightX() + this.rightW() - 62;
    }

    private int removeX() {
        return this.rightX() + this.rightW() - 22;
    }

    // ---- rendering -----------------------------------------------------

    private float openProgress() {
        long elapsed = System.currentTimeMillis() - this.openTime;
        return UiKit.easeOut(Math.min(1f, elapsed / 220f));
    }

    private void updateHover(int mouseX, int mouseY, float dt) {
        int size = this.results.size() + this.addedOrder.size();
        if (this.rowHover.length != size) {
            float[] resized = new float[size];
            System.arraycopy(this.rowHover, 0, resized, 0, Math.min(this.rowHover.length, size));
            this.rowHover = resized;
        }
        int row = (int) ((mouseY - this.listTop()) / ROW_H);
        boolean inside = mouseY >= this.listTop() && mouseY < this.listBottom();
        for (int i = 0; i < size; i++) {
            boolean hovered = false;
            if (inside && row == i - (i < this.results.size() ? this.resultScroll : 0)) {
                hovered = i < this.results.size()
                        ? mouseX >= this.leftX() && mouseX <= this.leftX() + this.leftW()
                        : mouseX >= this.rightX() && mouseX <= this.rightX() + this.rightW();
            }
            this.rowHover[i] = UiKit.approach(this.rowHover[i], hovered ? 1f : 0f, dt, 14f);
        }
    }

    private float hoverOf(int index) {
        return index >= 0 && index < this.rowHover.length ? this.rowHover[index] : 0f;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float dt = UiKit.frameDelta(this.lastFrameNanos);
        this.lastFrameNanos = System.nanoTime();
        this.updateHover(mouseX, mouseY, dt);
        float p = this.openProgress();

        graphics.fill(0, 0, this.width, this.height, UiKit.scaleAlpha(UiKit.SCRIM, p));
        graphics.pose().pushPose();
        float scale = 0.985f + 0.015f * p;
        graphics.pose().translate(this.width / 2f, this.height / 2f, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-this.width / 2f, -this.height / 2f, 0);
        graphics.setColor(1f, 1f, 1f, p);

        UiKit.glassPanel(graphics, this.panelX(), this.panelY(), this.panelW(), this.panelH(), 12);
        UiKit.drawScaled(graphics, this.font, "附魔编辑", this.panelX() + 18, this.panelY() + 10, 1.4f, UiKit.TEXT_PRIMARY);
        UiKit.drawShadowed(graphics, this.font, "左侧搜索并点击添加 · 右侧调整等级或移除",
                this.panelX() + 18, this.panelY() + 27, UiKit.TEXT_MUTED);

        // column headers
        UiKit.accentBar(graphics, this.leftX(), this.panelY() + 40, 9, UiKit.ACCENT);
        UiKit.drawShadowed(graphics, this.font, "可选附魔 (" + this.results.size() + ")",
                this.leftX() + 12, this.panelY() + 38, UiKit.TEXT_SECONDARY);
        UiKit.accentBar(graphics, this.rightX(), this.panelY() + 40, 9, UiKit.SUCCESS);
        UiKit.drawShadowed(graphics, this.font, "已添加 (" + this.addedOrder.size() + ")",
                this.rightX() + 12, this.panelY() + 38, UiKit.TEXT_SECONDARY);

        UiKit.inputBackdrop(graphics, this.leftX() + 2, this.panelY() + HEADER_H + 6,
                Math.max(80, this.leftW() - 4), INPUT_H, this.search != null && this.search.isFocused(), 0f);

        graphics.enableScissor(this.panelX(), this.listTop(), this.panelX() + this.panelW(), this.listBottom());
        this.renderResultColumn(graphics);
        this.renderAddedColumn(graphics);
        graphics.disableScissor();

        if (this.addedOrder.isEmpty()) {
            UiKit.drawScaledCentered(graphics, this.font, "还没有添加附魔",
                    this.rightX() + this.rightW() / 2, this.listTop() + 6, 1f, UiKit.TEXT_MUTED);
        }

        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderResultColumn(GuiGraphics graphics) {
        int top = this.listTop();
        int visible = this.rowsVisible();
        for (int i = 0; i < visible; i++) {
            int index = this.resultScroll + i;
            if (index >= this.results.size()) {
                break;
            }
            Enchantment enchantment = this.results.get(index);
            int y = top + i * ROW_H;
            boolean present = this.edits.containsKey(enchantment);
            UiKit.rowCard(graphics, this.leftX(), y, this.leftW(), ROW_H - 2, this.hoverOf(index));
            UiKit.drawShadowed(graphics, this.font,
                    UiKit.trim(this.font, enchantment.getFullname(1).getString(), this.leftW() - 34),
                    this.leftX() + 8, y + 5,
                    present ? 0xFFA7F3D0 : UiKit.TEXT_SECONDARY);
            UiKit.drawShadowed(graphics, this.font, present ? "✔" : "＋",
                    this.leftX() + this.leftW() - 16, y + 5,
                    present ? UiKit.SUCCESS : UiKit.TEXT_MUTED);
        }
        if (this.results.isEmpty()) {
            UiKit.drawScaledCentered(graphics, this.font, "没有匹配的附魔",
                    this.leftX() + this.leftW() / 2, top + 6, 1f, UiKit.TEXT_MUTED);
        }
    }

    private void renderAddedColumn(GuiGraphics graphics) {
        int top = this.listTop();
        int visible = this.rowsVisible();
        int minus = this.minusX();
        int remove = this.removeX();
        for (int i = 0; i < visible; i++) {
            int index = this.addedScroll + i;
            if (index >= this.addedOrder.size()) {
                break;
            }
            Enchantment enchantment = this.addedOrder.get(index);
            int y = top + i * ROW_H;
            int level = this.edits.getOrDefault(enchantment, 1);
            UiKit.rowCard(graphics, this.rightX(), y, this.rightW(), ROW_H - 2,
                    this.hoverOf(this.results.size() + index));
            UiKit.drawShadowed(graphics, this.font,
                    UiKit.trim(this.font, enchantment.getFullname(1).getString(), minus - this.rightX() - 14),
                    this.rightX() + 8, y + 5,
                    enchantment.isCurse() ? 0xFFFF6B6B : UiKit.TEXT_PRIMARY);

            String label = "Lv." + level;
            graphics.drawString(this.font, label, minus - 8 - this.font.width(label), y + 5,
                    UiKit.ACCENT_BRIGHT, false);

            boolean canDown = level > 1;
            boolean canUp = level < enchantment.getMaxLevel();
            UiKit.fillRounded(graphics, minus, y + 1, 18, 18, 6,
                    canDown ? 0x40FFFFFF : 0x18FFFFFF);
            UiKit.drawShadowedCentered(graphics, this.font, Component.literal("−"), minus + 9, y + 5,
                    canDown ? UiKit.TEXT_PRIMARY : UiKit.TEXT_MUTED);
            UiKit.fillRounded(graphics, minus + 20, y + 1, 18, 18, 6,
                    canUp ? 0x40FFFFFF : 0x18FFFFFF);
            UiKit.drawShadowedCentered(graphics, this.font, Component.literal("＋"), minus + 29, y + 5,
                    canUp ? UiKit.TEXT_PRIMARY : UiKit.TEXT_MUTED);

            UiKit.fillRounded(graphics, remove, y + 1, 18, 18, 6, UiKit.scaleAlpha(UiKit.DANGER_DEEP, 0.85f));
            UiKit.drawShadowedCentered(graphics, this.font, Component.literal("✕"), remove + 9, y + 5,
                    UiKit.TEXT_PRIMARY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
