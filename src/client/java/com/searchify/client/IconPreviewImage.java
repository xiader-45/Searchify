package com.searchify.client;

import dev.isxander.yacl3.gui.image.ImageRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

public class IconPreviewImage implements ImageRenderer {
    private final Identifier iconId;

    public IconPreviewImage(Identifier iconId) {
        this.iconId = iconId;
    }

    @Override
    public int render(DrawContext context, int startX, int startY, int width, float deltaTicks) {
        int size = 40;
        int x = startX + (width - size) / 2;
        int y = startY + 15;

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, iconId, x, y, size, size);

        return 70;
    }

    @Override
    public void close() {}
}