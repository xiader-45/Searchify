package com.searchify.client;

import dev.isxander.yacl3.gui.image.ImageRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class SearchifyPreviewImage implements ImageRenderer {

    public static SearchifyConfig.DisplayMode currentMode = SearchifyConfig.displayMode;
    public static int currentSpeed = SearchifyConfig.animationSpeed;
    public static int currentColor = SearchifyConfig.highlightColor;
    public static int currentGhostAlpha = SearchifyConfig.ghostAlpha;
    public static int currentPulseScale = SearchifyConfig.pulseScale;

    private static final Identifier SLOT_TEX = Identifier.ofVanilla("container/slot");
    private static final Identifier DISABLED_SLOT_TEX = Identifier.ofVanilla("container/crafter/disabled_slot");

    private final ItemStack[] previewItems;
    private final String[] targetQueries;

    private float currentFade = 1.0f;
    private boolean isSearching = false;
    private long lastToggleTime = System.currentTimeMillis();

    public SearchifyPreviewImage() {
        this(new ItemStack[]{
                Items.DIAMOND.getDefaultStack(),
                Items.COOKED_BEEF.getDefaultStack(),
                Items.DIAMOND.getDefaultStack(),
                Items.APPLE.getDefaultStack(),
                Items.IRON_INGOT.getDefaultStack(),
                Items.DIAMOND.getDefaultStack(),
                Items.REDSTONE.getDefaultStack(),
                Items.DIAMOND.getDefaultStack()
        }, "diamond");
    }

    public SearchifyPreviewImage(ItemStack[] items, String... queries) {
        this.previewItems = items;
        this.targetQueries = queries;
    }

    private boolean isMatch(ItemStack stack) {
        String itemName = stack.getItem().toString().toLowerCase();
        for (String q : targetQueries) {
            if (itemName.contains(q)) return true;
        }
        return false;
    }

    private float getAnimationScale(float fade) {
        if (fade <= 0.0f) return 0.0f;
        if (fade >= 1.0f) return 1.0f;
        if (fade > 0.6f) {
            float t = (1.0f - fade) / 0.4f;
            return 1.0f + (t * 0.2f);
        } else {
            float t = fade / 0.6f;
            return t * 1.2f;
        }
    }

    private float getPulseScale(float fade) {
        float progress = 1.0f - fade;
        if (progress <= 0.0f) return 1.0f;

        float maxScale = currentPulseScale / 100.0f;
        float extra = maxScale - 1.0f;

        if (progress < 0.6f) {
            float t = (float) Math.sin((progress / 0.6f) * Math.PI / 2.0);
            return 1.0f + (t * extra);
        } else {
            float t = 0.5f - 0.5f * (float) Math.cos(((progress - 0.6f) / 0.4f) * Math.PI);
            return maxScale - (t * (extra * 0.4f));
        }
    }

    @Override
    public int render(DrawContext context, int startX, int startY, int width, float deltaTicks) {
        long now = System.currentTimeMillis();
        if (now - lastToggleTime > 1200) {
            isSearching = !isSearching;
            lastToggleTime = now;
        }

        float targetFade = isSearching ? 0.0f : 1.0f;
        if (currentFade != targetFade) {
            float step = 0.3f * (currentSpeed / 100.0f) * deltaTicks;
            currentFade = isSearching ? Math.max(currentFade - step, 0.0f) : Math.min(currentFade + step, 1.0f);
        }

        int cols = 4;
        int slotSize = 18;
        int gridWidth = cols * slotSize;

        int x = startX + (width - gridWidth) / 2;
        int y = startY + 15;

        Text titleText = isSearching ? Text.translatable("searchify.preview.searching", (Object) targetQueries) : Text.translatable("searchify.preview.empty");
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(titleText);
        context.drawText(MinecraftClient.getInstance().textRenderer, titleText, startX + (width - textWidth) / 2, y - 12, isSearching ? 0x55FF55 : 0xAAAAAA, false);

        for (int i = 0; i < previewItems.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int slotX = x + col * slotSize;
            int slotY = y + row * slotSize;
            ItemStack stack = previewItems[i];
            boolean match = isMatch(stack);

            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, SLOT_TEX, slotX, slotY, slotSize, slotSize);

            switch (currentMode) {
                case GHOST -> {
                    context.drawItem(stack, slotX + 1, slotY + 1);
                    if (isSearching && !match) {
                        int dimColor = ((int) (currentGhostAlpha / 100.0f * 255.0f) << 24) | 0x8b8b8b;
                        context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, dimColor);
                    }
                }
                case COLOR -> {
                    if (isSearching && match) {
                        context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, currentColor | 0xFF000000);
                    }
                    context.drawItem(stack, slotX + 1, slotY + 1);
                }
                case OUTLINE -> {
                    if (isSearching && match) {
                        int c = currentColor | 0xFF000000;
                        context.fill(slotX, slotY, slotX + 18, slotY + 1, c);
                        context.fill(slotX, slotY + 17, slotX + 18, slotY + 18, c);
                        context.fill(slotX, slotY + 1, slotX + 1, slotY + 17, c);
                        context.fill(slotX + 17, slotY + 1, slotX + 18, slotY + 17, c);
                    }
                    context.drawItem(stack, slotX + 1, slotY + 1);
                }
                case TEXTURE -> {
                    context.drawItem(stack, slotX + 1, slotY + 1);
                    if (isSearching && !match) {
                        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, DISABLED_SLOT_TEX, slotX, slotY, slotSize, slotSize);
                    }
                }
                case ANIMATION, PULSE -> {
                    float renderScale = 1.0f;
                    boolean shouldTransform = false;

                    if (currentMode == SearchifyConfig.DisplayMode.ANIMATION && !match) {
                        renderScale = getAnimationScale(currentFade);
                        shouldTransform = true;
                    } else if (currentMode == SearchifyConfig.DisplayMode.PULSE && match) {
                        renderScale = getPulseScale(currentFade);
                        shouldTransform = true;
                    }

                    if (shouldTransform) {
                        if (renderScale > 0.0f) {
                            float itemCenterX = slotX + 9f;
                            float itemCenterY = slotY + 9f;

                            context.getMatrices().pushMatrix();
                            context.getMatrices().translate(itemCenterX, itemCenterY);
                            context.getMatrices().scale(renderScale, renderScale);
                            context.getMatrices().translate(-itemCenterX, -itemCenterY);
                            context.drawItem(stack, slotX + 1, slotY + 1);
                            context.getMatrices().popMatrix();
                        }
                    } else {
                        context.drawItem(stack, slotX + 1, slotY + 1);
                    }
                }
            }
        }

        return 70;
    }

    @Override
    public void close() {}
}