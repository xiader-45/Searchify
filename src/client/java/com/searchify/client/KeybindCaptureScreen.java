package com.searchify.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.input.KeyInput;

public class KeybindCaptureScreen extends Screen {
    private final Screen parentYaclScreen;

    public KeybindCaptureScreen(Screen parentYaclScreen) {
        super(Text.literal("Keybind Capture"));
        this.parentYaclScreen = parentYaclScreen;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.parentYaclScreen != null) {
            this.parentYaclScreen.render(context, mouseX, mouseY, delta);
        }

        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xCC000000);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("searchify.message.press_key"), this.width / 2, this.height / 2 - 10, 0xFFFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("searchify.message.press_esc_to_cancel"), this.width / 2, this.height / 2 + 10, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (this.client == null) return false;

        int keyCode = input.key();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.client.setScreen(this.parentYaclScreen);
            return true;
        }

        SearchifyConfig.searchKeybind = InputUtil.Type.KEYSYM.createFromCode(keyCode).getTranslationKey();
        SearchifyConfig.save();

        this.client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        this.client.setScreen(ModMenuIntegration.createScreen(ModMenuIntegration.parentScreen));

        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parentYaclScreen);
        }
    }
}