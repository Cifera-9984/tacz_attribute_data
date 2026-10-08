package com.core.attribute.tacz.client;

import com.core.attribute.tacz.data.AttributeLine;
import com.core.attribute.tacz.data.AttributeType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Lore (description) editor screen, restyled to match the modern glass theme.
 * Every behaviour of the original screen is preserved.
 */
public class LoreEditorScreen extends Screen {

    private static final int ROW_HEIGHT = 34;
    private static final int ROWS_PER_PAGE_LIMIT = 6;

    private final AttributeEditorScreen parent;
    private final ItemStack previewStack;
    private final List<AttributeLine> loreLines;
    private final List<RowEditor> rowEditors = new ArrayList<>();
    private EditBox newLoreBox;
    private String newLoreDraft = "";
    private int page;
    private boolean closing;

    private long openTime = System.currentTimeMillis();
    private long lastFrameNanos;
    private float[] rowHoverValues = new float[0];

    public LoreEditorScreen(AttributeEditorScreen parent, ItemStack previewStack, List<AttributeLine> loreLines) {
        super(Component.literal("描述编辑"));
        this.parent = parent;
        this.previewStack = previewStack.copy();
        this.loreLines = loreLines;
    }

    // ---- layout --------------------------------------------------------

    private int panelW() {
        return Math.min(720, this.width - 40);
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

    private int rowsTop() {
        return panelY() + 88;
    }

    private int rowsPerPage() {
        int available = panelH() - 268;
        return Math.max(1, Math.min(ROWS_PER_PAGE_LIMIT, available / ROW_HEIGHT));
    }

    private int addRowY() {
        return rowsTop() + rowsPerPage() * ROW_HEIGHT + 8;
    }

    private int clipRowY() {
        return addRowY() + 30;
    }

    private int bottomY() {
        return panelY() + panelH() - 40;
    }

    private record RowLayout(int cardX, int cardY, int cardW, int cardH,
                             int deleteX, int inputX, int inputY, int inputW, int inputH,
                             int arrowsX) {
    }

    private RowLayout layoutRow(int pageIndex) {
        int cx = panelX() + 18;
        int cw = panelW() - 36;
        int cy = rowsTop() + pageIndex * ROW_HEIGHT;
        int ch = ROW_HEIGHT - 4;
        int actionsTotal = 3 * 20 + 2 * 2;
        int arrowsX = cx + cw - 6 - actionsTotal;
        int inputX = cx + 34;
        int inputW = Math.max(70, arrowsX - inputX - 6);
        return new RowLayout(cx, cy, cw, ch, cx + 6, inputX, cy + 6, inputW, 18, arrowsX);
    }

    // ---- widget building ----------------------------------------------

    @Override
    protected void init() {
        if (this.loreLines.isEmpty()) {
            this.loreLines.add(AttributeLine.of(AttributeType.CUSTOM, ""));
        }
        this.syncRowBoxes();
        this.rebuildEditorWidgets();
    }

    private void rebuildEditorWidgets() {
        this.clearWidgets();
        this.rowEditors.clear();
        int rowsPerPage = this.rowsPerPage();
        this.clampPage(rowsPerPage);
        int start = this.page * rowsPerPage;
        int end = Math.min(this.loreLines.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            this.addRowWidgets(this.loreLines.get(i), i, i - start);
        }

        int addY = addRowY();
        int addInputX = panelX() + 18 + 8;
        int addButtonX = panelX() + panelW() - 18 - 76;
        this.newLoreBox = new GlassEditBox(this.font, addInputX, addY + 2, Math.max(80, addButtonX - addInputX - 14), 18, Component.empty());
        this.newLoreBox.setMaxLength(96);
        this.newLoreBox.setBordered(false);
        this.newLoreBox.setValue(!this.newLoreDraft.isBlank() ? this.newLoreDraft : this.defaultLoreText());
        this.newLoreBox.setTextColor(0xFFB07CFF);
        this.newLoreBox.setHint(Component.literal(this.defaultLoreText()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.newLoreBox);
        this.addRenderableWidget(new PurpleButton(addButtonX, addY, 76, 22, Component.literal("＋ 新增"),
                b -> {
                    this.syncRowBoxes();
                    this.loreLines.add(AttributeLine.of(AttributeType.CUSTOM, this.newLoreDraft));
                    this.newLoreDraft = "";
                    this.page = Math.max(0, (this.loreLines.size() - 1) / this.rowsPerPage());
                    this.rebuildEditorWidgets();
                }, UiKit.TEXT_PRIMARY, PurpleButton.Style.PRIMARY));

        int clipY = clipRowY();
        this.addRenderableWidget(new PurpleButton(panelX() + 18, clipY, 110, 22, Component.literal("复制描述"),
                b -> this.copyLoreToClipboard(), UiKit.TEXT_SECONDARY, PurpleButton.Style.GLASS));
        this.addRenderableWidget(new PurpleButton(panelX() + 18 + 118, clipY, 82, 22, Component.literal("粘贴"),
                b -> this.pasteLoreFromClipboard(), UiKit.TEXT_SECONDARY, PurpleButton.Style.GLASS));

        int bottom = bottomY();
        this.addRenderableWidget(new PurpleButton(panelX() + 18, bottom, 84, 26, Component.literal("返回"),
                b -> this.closeEditor(), UiKit.TEXT_PRIMARY, PurpleButton.Style.GLASS));
        PurpleButton previous = new PurpleButton(panelX() + panelW() / 2 - 110, bottom, 80, 26, Component.literal("上一页"),
                b -> {
                    this.syncRowBoxes();
                    if (this.page > 0) {
                        this.page--;
                    }
                    this.rebuildEditorWidgets();
                }, UiKit.TEXT_SECONDARY, PurpleButton.Style.GLASS);
        previous.active = this.page > 0;
        this.addRenderableWidget(previous);
        PurpleButton next = new PurpleButton(panelX() + panelW() - 98, bottom, 80, 26, Component.literal("下一页"),
                b -> {
                    this.syncRowBoxes();
                    if ((this.page + 1) * rowsPerPage < this.loreLines.size()) {
                        this.page++;
                    }
                    this.rebuildEditorWidgets();
                }, UiKit.TEXT_SECONDARY, PurpleButton.Style.GLASS);
        next.active = (this.page + 1) * rowsPerPage < this.loreLines.size();
        this.addRenderableWidget(next);
    }

    private void addRowWidgets(AttributeLine line, int index, int pageIndex) {
        RowLayout layout = this.layoutRow(pageIndex);
        int rowY = layout.cardY();

        this.addRenderableWidget(new PurpleButton(layout.deleteX(), rowY + 5, 20, 20, Component.literal("✕"),
                b -> {
                    this.syncRowBoxes();
                    if (index >= 0 && index < this.loreLines.size()) {
                        this.loreLines.remove(index);
                    }
                    if (this.loreLines.isEmpty()) {
                        this.loreLines.add(AttributeLine.of(AttributeType.CUSTOM, ""));
                    }
                    if (this.page > 0 && this.page * this.rowsPerPage() >= this.loreLines.size()) {
                        this.page--;
                    }
                    this.rebuildEditorWidgets();
                }, UiKit.TEXT_PRIMARY, PurpleButton.Style.DANGER));

        EditBox box = new GlassEditBox(this.font, layout.inputX() + 8, layout.inputY(), layout.inputW() - 16, 18, Component.empty());
        box.setMaxLength(96);
        box.setBordered(false);
        box.setValue(line.editorValue());
        box.setTextColor(0xFFB07CFF);
        box.setHint(line.editorValue().isBlank() ? Component.literal("Lore " + (index + 1)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY) : null);
        this.rowEditors.add(new RowEditor(index, box));
        this.addRenderableWidget(box);

        this.addRenderableWidget(new PurpleButton(layout.arrowsX(), rowY + 5, 20, 20, Component.literal("↑"),
                b -> this.moveLine(index, -1), UiKit.TEXT_SECONDARY, PurpleButton.Style.GLASS));
        this.addRenderableWidget(new PurpleButton(layout.arrowsX() + 22, rowY + 5, 20, 20, Component.literal("↓"),
                b -> this.moveLine(index, 1), UiKit.TEXT_SECONDARY, PurpleButton.Style.GLASS));
        this.addRenderableWidget(new PurpleButton(layout.arrowsX() + 44, rowY + 5, 20, 20, Component.literal("复"),
                b -> this.copyLine(index), UiKit.TEXT_SECONDARY, PurpleButton.Style.GLASS));
    }

    // ---- behaviour (unchanged) ---------------------------------------

    private void moveLine(int index, int delta) {
        this.syncRowBoxes();
        int target = index + delta;
        if (index < 0 || index >= this.loreLines.size() || target < 0 || target >= this.loreLines.size()) {
            return;
        }
        AttributeLine line = this.loreLines.remove(index);
        this.loreLines.add(target, line);
        this.page = Math.max(0, target / this.rowsPerPage());
        this.rebuildEditorWidgets();
    }

    private void copyLine(int index) {
        this.syncRowBoxes();
        if (index >= 0 && index < this.loreLines.size()) {
            this.loreLines.add(index + 1, this.loreLines.get(index));
            this.page = Math.max(0, (index + 1) / this.rowsPerPage());
        }
        this.rebuildEditorWidgets();
    }

    private void syncRowBoxes() {
        if (this.newLoreBox != null) {
            this.newLoreDraft = this.newLoreBox.getValue();
        }
        for (RowEditor editor : this.rowEditors) {
            if (editor.index() < 0 || editor.index() >= this.loreLines.size()) {
                continue;
            }
            this.loreLines.set(editor.index(), this.loreLines.get(editor.index()).withValueText(editor.box().getValue()));
        }
    }

    private void copyLoreToClipboard() {
        this.syncRowBoxes();
        if (this.minecraft == null) {
            return;
        }
        ArrayList<String> copiedLines = new ArrayList<>(this.loreLines.size());
        for (AttributeLine line : this.loreLines) {
            copiedLines.add(line == null || line.text() == null ? "" : line.text());
        }
        this.minecraft.keyboardHandler.setClipboard(String.join("\n", copiedLines));
    }

    private void pasteLoreFromClipboard() {
        if (this.minecraft == null) {
            return;
        }
        String clipboard = this.minecraft.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            return;
        }
        String[] pastedLines = clipboard.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int contentEnd = pastedLines.length;
        while (contentEnd > 0 && pastedLines[contentEnd - 1].isBlank()) {
            contentEnd--;
        }
        if (contentEnd == 0) {
            return;
        }
        this.syncRowBoxes();
        this.loreLines.clear();
        for (int i = 0; i < contentEnd; i++) {
            this.loreLines.add(new AttributeLine(AttributeType.CUSTOM, pastedLines[i], false));
        }
        this.page = 0;
        this.rebuildEditorWidgets();
    }

    private void closeEditor() {
        if (this.closing) {
            return;
        }
        this.closing = true;
        this.syncRowBoxes();
        this.loreLines.removeIf(line -> line == null || line.text().isBlank());
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        this.closeEditor();
    }

    private String defaultLoreText() {
        return "Lore " + (this.loreLines.size() + 1);
    }

    private void clampPage(int rowsPerPage) {
        int maxPage = Math.max(0, (this.loreLines.size() - 1) / rowsPerPage);
        this.page = Math.max(0, Math.min(this.page, maxPage));
    }

    // ---- rendering -----------------------------------------------------

    private float openProgress() {
        long elapsed = System.currentTimeMillis() - this.openTime;
        return UiKit.easeOut(Math.min(1f, elapsed / 220f));
    }

    private float rowHover(int index) {
        return index >= 0 && index < this.rowHoverValues.length ? this.rowHoverValues[index] : 0f;
    }

    private void updateRowHover(int mouseY, float dt) {
        int rowsPerPage = this.rowsPerPage();
        int start = this.page * rowsPerPage;
        int end = Math.min(this.loreLines.size(), start + rowsPerPage);
        if (this.rowHoverValues.length < end) {
            float[] grown = new float[end];
            System.arraycopy(this.rowHoverValues, 0, grown, 0, this.rowHoverValues.length);
            this.rowHoverValues = grown;
        }
        for (int i = start; i < end; i++) {
            RowLayout layout = this.layoutRow(i - start);
            boolean hovered = mouseY >= layout.cardY() && mouseY < layout.cardY() + layout.cardH();
            this.rowHoverValues[i] = UiKit.approach(this.rowHoverValues[i], hovered ? 1f : 0f, dt, 12f);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float dt = UiKit.frameDelta(this.lastFrameNanos);
        this.lastFrameNanos = System.nanoTime();
        this.updateRowHover(mouseY, dt);
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
        UiKit.drawScaled(graphics, this.font, "描述编辑", panelX + 20, panelY + 16, 1.4f, UiKit.TEXT_PRIMARY);
        UiKit.drawShadowed(graphics, this.font, "每行一条 · 支持下划线与 & 颜色代码", panelX + 20, panelY + 38, UiKit.TEXT_MUTED);
        int rowsPerPageNow = this.rowsPerPage();
        int totalPages = Math.max(1, (this.loreLines.size() + rowsPerPageNow - 1) / rowsPerPageNow);
        String pageInfo = "第 " + (this.page + 1) + " / " + totalPages + " 页";
        graphics.drawString(this.font, pageInfo, panelX + panelW - 20 - this.font.width(pageInfo), panelY + 20, UiKit.TEXT_MUTED, false);

        // small item preview in the header
        graphics.pose().pushPose();
        graphics.pose().translate(panelX + panelW - 76, panelY + 44, 0);
        graphics.pose().scale(2f, 2f, 1f);
        graphics.renderItem(this.previewStack, 0, 0);
        graphics.pose().popPose();

        graphics.fill(panelX + 18, rowsTop() - 8, panelX + panelW - 18, rowsTop() - 7, 0x22FFFFFF);

        // lore rows
        int start = this.page * rowsPerPageNow;
        int end = Math.min(this.loreLines.size(), start + rowsPerPageNow);
        for (int i = start; i < end; i++) {
            final int lineIndex = i;
            RowLayout layout = this.layoutRow(i - start);
            float hover = this.rowHover(i);
            UiKit.rowCard(graphics, layout.cardX(), layout.cardY(), layout.cardW(), layout.cardH(), hover);
            UiKit.accentBar(graphics, layout.cardX() + 31, layout.cardY() + 7, layout.cardH() - 14, UiKit.ACCENT);
            UiKit.inputBackdrop(graphics, layout.inputX(), layout.inputY() - 2, layout.inputW(), layout.inputH() + 4,
                    this.rowEditors.stream().anyMatch(r -> r.index() == lineIndex && r.box().isFocused()), hover * 0.6f);
        }

        // add-new input backdrop
        int addY = addRowY();
        UiKit.inputBackdrop(graphics, panelX() + 26, addY, panelW() - 36 - 84, 22,
                this.newLoreBox != null && this.newLoreBox.isFocused(), 0f);

        // clipboard divider
        graphics.fill(panelX() + 18, clipRowY() - 8, panelX() + panelW - 18, clipRowY() - 7, 0x22FFFFFF);

        UiKit.drawShadowed(graphics, this.font, "ESC 返回 · 空白行会在返回时自动清理", panelX() + 18, bottomY() + 32, UiKit.TEXT_MUTED);

        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RowEditor(int index, EditBox box) {
    }
}