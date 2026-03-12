package com.searchify.client.mixin;

import com.searchify.client.SearchifyConfig;
import com.searchify.util.ItemSearchEngine;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.Click;
import net.minecraft.entity.player.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow @Final protected ScreenHandler handler;
    @Shadow protected Slot focusedSlot;

    @Unique private TextFieldWidget searchBox;

    @Unique private static final Identifier TEX_FIELD = Identifier.of("searchify", "text_field");
    @Unique private static final Identifier TEX_SEARCH = Identifier.of("searchify", "search");
    @Unique private static final Identifier TEX_SEARCH_HIGHLIGHT = Identifier.of("searchify", "search_highlight");
    @Unique private static final Identifier TEX_LOCK = Identifier.of("searchify", "lock");
    @Unique private static final Identifier TEX_UNLOCK = Identifier.of("searchify", "unlock");
    @Unique private static final Identifier TEX_HISTORY = Identifier.of("searchify", "history");
    @Unique private static final Identifier DISABLED_SLOT_TEX = Identifier.ofVanilla("container/crafter/disabled_slot");

    @Unique private static final int MAX_BG_WIDTH = 80;
    @Unique private static final int BG_HEIGHT = 12;
    @Unique private static final int BTN_SIZE = 12;
    @Unique private static final int LOCK_SIZE = 5;
    @Unique private static final int HISTORY_SIZE = 10;

    @Unique private boolean isSupportedCache = false;
    @Unique private boolean isHistoryOpen = false;
    @Unique private int currentBgWidth = 80;
    @Unique private float currentDeltaTicks = 1.0f;
    @Unique private float historyAnimProgress = 1.0f;

    @Unique private final Int2FloatMap fadeMap = new Int2FloatOpenHashMap();
    @Unique private String cachedSearchQuery = "";
    @Unique private String lastRawQuery = "";

    // Memory Cache
    @Unique private final ItemStack[] cachedStacks = new ItemStack[300];
    @Unique private final boolean[] cachedMatches = new boolean[300];
    @Unique private final List<Text> cachedHistoryTexts = new ArrayList<>();

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Unique
    private boolean checkUnsupportedScreen() {
        if (this.client == null || this.client.currentScreen == null) return true;
        Screen screen = this.client.currentScreen;
        if (!(screen instanceof GenericContainerScreen) && !(screen instanceof ShulkerBoxScreen)) return true;

        String type = "unknown";
        if (screen instanceof ShulkerBoxScreen) {
            type = "shulker";
        } else if (screen instanceof GenericContainerScreen && this.client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult blockHit && this.client.world != null) {
            net.minecraft.block.Block block = this.client.world.getBlockState(blockHit.getBlockPos()).getBlock();
            String id = Registries.BLOCK.getId(block).getPath();
            if (id.contains("barrel")) type = "barrel";
            else if (id.contains("ender_chest")) type = "ender_chest";
            else if (id.contains("trapped_chest")) type = "trapped_chest";
            else if (id.contains("copper_chest")) type = "copper_chest";
            else type = "chest";
        }

        return switch (type) {
            case "chest" -> !SearchifyConfig.enableChests;
            case "barrel" -> !SearchifyConfig.enableBarrels;
            case "ender_chest" -> !SearchifyConfig.enableEnderChests;
            case "trapped_chest" -> !SearchifyConfig.enableTrappedChests;
            case "copper_chest" -> !SearchifyConfig.enableCopperChests;
            case "shulker" -> !SearchifyConfig.enableShulkerBoxes;
            default -> false;
        };
    }

    @Unique
    private void playClickSound() {
        if (this.client != null) {
            this.client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Unique
    private void updateHistoryCache() {
        this.cachedHistoryTexts.clear();
        for (String s : SearchifyConfig.searchHistory) {
            this.cachedHistoryTexts.add(Text.literal(s));
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        this.isSupportedCache = !checkUnsupportedScreen();
        if (!this.isSupportedCache || !SearchifyConfig.isEnabled) {
            this.searchBox = null;
            return;
        }

        updateHistoryCache();

        String savedText = SearchifyConfig.autoLock ? SearchifyConfig.savedSearchQuery : "";
        boolean wasVisible = SearchifyConfig.autoLock || SearchifyConfig.autoFocusSearchBar;
        boolean wasFocused = SearchifyConfig.autoFocusSearchBar;

        if (this.searchBox != null) {
            savedText = this.searchBox.getText();
            wasVisible = this.searchBox.isVisible();
            wasFocused = this.searchBox.isFocused();
        }

        int titleWidth = this.textRenderer.getWidth(this.title);
        int dynamicStartX = Math.max(this.backgroundWidth - MAX_BG_WIDTH - 7, 8 + titleWidth + 4);

        this.currentBgWidth = Math.max(this.backgroundWidth - 7 - dynamicStartX, 35);
        int startX = this.x + (this.backgroundWidth - 7 - this.currentBgWidth);

        this.searchBox = new TextFieldWidget(this.textRenderer, startX + 2, this.y + 6, this.currentBgWidth - 12, BG_HEIGHT, Text.translatable("itemGroup.search")) {
            @Override
            public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TEX_FIELD, this.getX() - 2, this.getY() - 2, currentBgWidth, BG_HEIGHT);
                super.renderWidget(context, mouseX, mouseY, delta);
            }
        };

        this.searchBox.setMaxLength(50);
        this.searchBox.setDrawsBackground(false);
        this.searchBox.setEditableColor(0xFFFFFFFF);
        this.searchBox.setText(savedText);
        this.searchBox.setVisible(wasVisible);
        this.searchBox.active = wasVisible;

        if (wasFocused) {
            this.setFocused(this.searchBox);
            this.searchBox.setFocused(true);
        }

        this.searchBox.setChangedListener(text -> {
            if (SearchifyConfig.autoLock) SearchifyConfig.savedSearchQuery = text;
        });

        this.addDrawableChild(this.searchBox);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        if (SearchifyConfig.isEnabled && this.searchBox != null) {
            String currentText = this.searchBox.getText().trim();
            if (!currentText.isEmpty() && SearchifyConfig.enableHistory) {
                SearchifyConfig.searchHistory.remove(currentText);
                SearchifyConfig.searchHistory.addFirst(currentText);
                if (SearchifyConfig.searchHistory.size() > 5) {
                    SearchifyConfig.searchHistory = SearchifyConfig.searchHistory.subList(0, 5);
                }
            }
            if (SearchifyConfig.autoLock) SearchifyConfig.savedSearchQuery = this.searchBox.getText();
            SearchifyConfig.save();
        }
    }

    @Inject(method = "renderMain", at = @At("HEAD"))
    private void onRenderMainHead(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        this.currentDeltaTicks = deltaTicks;
    }

    @Inject(method = "renderMain", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!this.isSupportedCache || this.searchBox == null) return;

        int startY = this.y + 4;

        if (!this.searchBox.isVisible()) {
            int buttonX = this.x + this.backgroundWidth - BTN_SIZE - 7;
            boolean isHovered = isHovering(mouseX, mouseY, buttonX, startY, BTN_SIZE, BTN_SIZE);
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, isHovered ? TEX_SEARCH_HIGHLIGHT : TEX_SEARCH, buttonX, startY, BTN_SIZE, BTN_SIZE);
        } else {
            int lockX = this.x + this.backgroundWidth - 7 - LOCK_SIZE - 3;
            int lockY = startY + (BG_HEIGHT - LOCK_SIZE) / 2;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SearchifyConfig.autoLock ? TEX_LOCK : TEX_UNLOCK, lockX, lockY, LOCK_SIZE, LOCK_SIZE);
        }

        if (SearchifyConfig.enableHistory) {
            int historyX = this.x + this.backgroundWidth + 1;
            int historyY = startY + 1;
            float scale = 1.0f;

            if (this.historyAnimProgress < 1.0f) {
                this.historyAnimProgress += 0.15f * currentDeltaTicks;
                if (this.historyAnimProgress > 1.0f) this.historyAnimProgress = 1.0f;
                scale = 1.0f - (float) (Math.sin(this.historyAnimProgress * Math.PI)) * 0.15f;
            }

            if (scale != 1.0f) {
                context.getMatrices().pushMatrix();
                float centerX = historyX + HISTORY_SIZE / 2.0f;
                float centerY = historyY + HISTORY_SIZE / 2.0f;
                context.getMatrices().translate(centerX, centerY);
                context.getMatrices().scale(scale, scale);
                context.getMatrices().translate(-centerX, -centerY);
            }

            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TEX_HISTORY, historyX, historyY, HISTORY_SIZE, HISTORY_SIZE);

            if (scale != 1.0f) context.getMatrices().popMatrix();

            if (this.isHistoryOpen && !this.cachedHistoryTexts.isEmpty()) {
                int maxTextWidth = this.cachedHistoryTexts.stream().mapToInt(this.textRenderer::getWidth).max().orElse(0);
                int dropDownWidth = maxTextWidth + 8;
                int textHeight = 12;
                int listHeight = this.cachedHistoryTexts.size() * textHeight + 4;

                context.fill(historyX, historyY + HISTORY_SIZE + 2, historyX + dropDownWidth, historyY + HISTORY_SIZE + 2 + listHeight, 0x88000000);

                int currentY = historyY + HISTORY_SIZE + 4;
                for (int i = 0; i < this.cachedHistoryTexts.size(); i++) {
                    boolean isHovered = isHovering(mouseX, mouseY, historyX, currentY, dropDownWidth, textHeight);
                    Text textToDraw = this.cachedHistoryTexts.get(i);
                    if (isHovered) textToDraw = textToDraw.copy().formatted(Formatting.UNDERLINE);
                    context.drawTextWithShadow(this.textRenderer, textToDraw, historyX + 4, currentY + 2, isHovered ? 0xFFFFFFFF : 0xFFAAAAAA);
                    currentY += textHeight;
                }
            }
        }
    }

    @Unique
    private boolean isHovering(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isSupportedCache || this.searchBox == null || (click.button() != 0 && click.button() != 1)) return;

        double mouseX = click.x();
        double mouseY = click.y();
        int startY = this.y + 4;
        int padding = 3;

        if (click.button() == 1) {
            if (this.searchBox.isVisible() && isHovering(mouseX, mouseY, this.searchBox.getX() - 2, this.searchBox.getY() - 2, this.currentBgWidth + 4, BG_HEIGHT + 4)) {
                playClickSound();
                this.searchBox.setText("");
                if (SearchifyConfig.autoLock) {
                    SearchifyConfig.savedSearchQuery = "";
                    SearchifyConfig.save();
                }
                this.lastRawQuery = "";
                this.cachedSearchQuery = "";
                cir.setReturnValue(true);
            }
            return;
        }

        if (SearchifyConfig.enableHistory) {
            int historyX = this.x + this.backgroundWidth + 1;
            int historyY = startY + 1;

            if (isHovering(mouseX, mouseY, historyX - padding, historyY - padding, HISTORY_SIZE + padding * 2, HISTORY_SIZE + padding * 2)) {
                playClickSound();
                this.isHistoryOpen = !this.isHistoryOpen;
                cir.setReturnValue(true);
                return;
            }

            if (this.isHistoryOpen && !SearchifyConfig.searchHistory.isEmpty()) {
                int maxTextWidth = this.cachedHistoryTexts.stream().mapToInt(this.textRenderer::getWidth).max().orElse(0);
                int dropDownWidth = maxTextWidth + 8;
                int textHeight = 12;
                int currentY = historyY + HISTORY_SIZE + 4;

                for (String historyItem : SearchifyConfig.searchHistory) {
                    if (isHovering(mouseX, mouseY, historyX, currentY, dropDownWidth, textHeight)) {
                        playClickSound();
                        if (!this.searchBox.isVisible()) {
                            this.searchBox.setVisible(true);
                            this.searchBox.active = true;
                            this.setFocused(this.searchBox);
                            this.searchBox.setFocused(true);
                        }

                        this.searchBox.setText(historyItem);
                        if (SearchifyConfig.autoLock) {
                            SearchifyConfig.savedSearchQuery = historyItem;
                            SearchifyConfig.save();
                        }
                        this.lastRawQuery = historyItem;
                        this.cachedSearchQuery = historyItem.trim().toLowerCase();
                        this.historyAnimProgress = 0.0f;
                        this.isHistoryOpen = false;

                        cir.setReturnValue(true);
                        return;
                    }
                    currentY += textHeight;
                }
            }
        }

        if (!this.searchBox.isVisible()) {
            int buttonX = this.x + this.backgroundWidth - BTN_SIZE - 7;
            if (isHovering(mouseX, mouseY, buttonX, startY, BTN_SIZE, BTN_SIZE)) {
                playClickSound();
                this.searchBox.setVisible(true);
                this.searchBox.active = true;
                this.setFocused(this.searchBox);
                this.searchBox.setFocused(true);
                cir.setReturnValue(true);
            }
        } else {
            int lockX = this.x + this.backgroundWidth - 7 - LOCK_SIZE - 3;
            int lockY = startY + (BG_HEIGHT - LOCK_SIZE) / 2;

            if (isHovering(mouseX, mouseY, lockX - padding, lockY - padding, LOCK_SIZE + padding * 2, LOCK_SIZE + padding * 2)) {
                playClickSound();
                SearchifyConfig.autoLock = !SearchifyConfig.autoLock;
                SearchifyConfig.savedSearchQuery = SearchifyConfig.autoLock ? this.searchBox.getText() : "";
                SearchifyConfig.save();
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isSupportedCache || this.searchBox == null) return;

        if (this.searchBox.isFocused() && this.searchBox.isVisible()) {
            if (this.searchBox.keyPressed(input)) {
                cir.setReturnValue(true);
            } else if (this.client != null && (
                    this.client.options.inventoryKey.matchesKey(input) ||
                            this.client.options.dropKey.matchesKey(input) ||
                            this.client.options.swapHandsKey.matchesKey(input))) {
                cir.setReturnValue(true);
            }
        } else {
            int targetKeyCode = net.minecraft.client.util.InputUtil.fromTranslationKey(SearchifyConfig.searchKeybind).getCode();

            if (input.key() == targetKeyCode && this.focusedSlot != null && this.focusedSlot.hasStack()) {
                ItemStack stack = this.focusedSlot.getStack();
                String localizedName = stack.getName().getString();

                this.searchBox.setVisible(true);
                this.searchBox.active = true;
                this.searchBox.setText(localizedName);

                if (SearchifyConfig.autoLock) {
                    SearchifyConfig.savedSearchQuery = localizedName;
                    SearchifyConfig.save();
                }

                playClickSound();
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private boolean isItemMatchingSearch(Slot slot) {
        if (!SearchifyConfig.isEnabled || this.searchBox == null || !this.searchBox.isVisible()) return true;

        if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.ANIMATION ||
                SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.TEXTURE ||
                SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.GHOST) {
            if (this.handler != null && !this.handler.getCursorStack().isEmpty()) return true;
        }

        String rawQuery = this.searchBox.getText();
        if (!rawQuery.equals(lastRawQuery)) {
            lastRawQuery = rawQuery;
            cachedSearchQuery = rawQuery.trim().toLowerCase();
        }

        if (cachedSearchQuery.isEmpty()) return true;
        if (!slot.hasStack()) return false;

        ItemStack stack = slot.getStack();
        int slotId = slot.id;

        // Pointer-based validation
        if (slotId >= 0 && slotId < cachedStacks.length) {
            if (cachedStacks[slotId] == stack) {
                return cachedMatches[slotId];
            }
        }

        boolean match = ItemSearchEngine.matches(stack, cachedSearchQuery);

        if (slotId >= 0 && slotId < cachedStacks.length) {
            cachedStacks[slotId] = stack;
            cachedMatches[slotId] = match;
        }
        return match;
    }

    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void onDrawSlotHead(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.isSupportedCache) return;
        if (!SearchifyConfig.searchInPlayerInventory && slot.inventory instanceof PlayerInventory) return;

        boolean isMatching = isItemMatchingSearch(slot);
        boolean isSearching = this.searchBox != null && this.searchBox.isVisible() && !this.searchBox.getText().trim().isEmpty();
        boolean hasCursorItem = this.handler != null && !this.handler.getCursorStack().isEmpty();

        if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE && hasCursorItem) isSearching = false;

        if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.OUTLINE) {
            if (isSearching && isMatching && slot.hasStack()) {
                int opaqueColor = SearchifyConfig.highlightColor | 0xFF000000;
                context.fill(slot.x - 1, slot.y - 1, slot.x + 18, slot.y + 1, opaqueColor);
                context.fill(slot.x - 1, slot.y + 17, slot.x + 18, slot.y + 18, opaqueColor);
                context.fill(slot.x - 1, slot.y + 1, slot.x, slot.y + 17, opaqueColor);
                context.fill(slot.x + 17, slot.y + 1, slot.x + 18, slot.y + 17, opaqueColor);
            }
            return;
        }

        if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.COLOR) {
            if (isSearching && isMatching && slot.hasStack()) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, SearchifyConfig.highlightColor | 0xFF000000);
            }
            return;
        }

        if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.TEXTURE) return;

        float targetFade = (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE) ?
                ((isSearching && isMatching) ? 1.0f : 0.0f) : (isMatching ? 1.0f : 0.0f);

        float currentFade = fadeMap.getOrDefault(slot.id, SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE ? 0.0f : 1.0f);

        if (currentFade != targetFade) {
            float step = 0.3f * (SearchifyConfig.animationSpeed / 100.0f) * currentDeltaTicks;
            currentFade = currentFade < targetFade ? Math.min(currentFade + step, targetFade) : Math.max(currentFade - step, targetFade);
            fadeMap.put(slot.id, currentFade);
        }

        if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.ANIMATION) {
            if (currentFade <= 0.0f && slot.hasStack()) {
                ci.cancel();
                return;
            }
            if (currentFade > 0.0f && currentFade < 1.0f && slot.hasStack()) {
                float renderScale = (currentFade > 0.6f) ? 1.0f + (((1.0f - currentFade) / 0.4f) * 0.2f) : (currentFade / 0.6f) * 1.2f;
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(slot.x + 8f, slot.y + 8f);
                context.getMatrices().scale(renderScale, renderScale);
                context.getMatrices().translate(-(slot.x + 8f), -(slot.y + 8f));
            }
        } else if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE && currentFade > 0.0f && slot.hasStack()) {
            float extra = (SearchifyConfig.pulseScale / 100.0f) - 1.0f;
            float renderScale = currentFade < 0.6f ? 1.0f + ((float) Math.sin((currentFade / 0.6f) * Math.PI / 2.0) * extra) :
                    (SearchifyConfig.pulseScale / 100.0f) - ((0.5f - 0.5f * (float) Math.cos(((currentFade - 0.6f) / 0.4f) * Math.PI)) * (extra * 0.4f));

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(slot.x + 8f, slot.y + 8f);
            context.getMatrices().scale(renderScale, renderScale);
            context.getMatrices().translate(-(slot.x + 8f), -(slot.y + 8f));
        }
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void onDrawSlotTail(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.isSupportedCache) return;
        if (!SearchifyConfig.searchInPlayerInventory && slot.inventory instanceof PlayerInventory) return;

        if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.GHOST) {
            if (!isItemMatchingSearch(slot) && slot.hasStack()) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ((int) (SearchifyConfig.ghostAlpha / 100.0f * 255.0f) << 24) | 0x8b8b8b);
            }
        } else if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.TEXTURE) {
            if (!isItemMatchingSearch(slot)) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, DISABLED_SLOT_TEX, slot.x - 1, slot.y - 1, 18, 18);
            }
        } else if (SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.ANIMATION || SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE) {
            float defaultFade = SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE ? 0.0f : 1.0f;
            float currentFade = fadeMap.getOrDefault(slot.id, defaultFade);
            if (currentFade > 0.0f && currentFade < 1.0f && slot.hasStack() && SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.ANIMATION) {
                context.getMatrices().popMatrix();
            } else if (currentFade > 0.0f && slot.hasStack() && SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE) {
                context.getMatrices().popMatrix();
            }
        }
    }
}