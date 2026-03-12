package com.searchify.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.awt.Color;

public class ModMenuIntegration implements ModMenuApi {

    public static Screen parentScreen = null;

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            parentScreen = parent;
            return createScreen(parent);
        };
    }

    private static Text getCleanKeyName(String translationKey) {
        if (translationKey == null) return Text.literal("UNKNOWN");

        if (translationKey.startsWith("key.keyboard.") && translationKey.length() == 14) {
            return Text.literal(translationKey.substring(13).toUpperCase());
        }

        return switch (translationKey) {
            case "key.keyboard.left.alt" -> Text.literal("L-ALT");
            case "key.keyboard.right.alt" -> Text.literal("R-ALT");
            case "key.keyboard.left.shift" -> Text.literal("L-SHIFT");
            case "key.keyboard.right.shift" -> Text.literal("R-SHIFT");
            case "key.keyboard.left.control" -> Text.literal("L-CTRL");
            case "key.keyboard.right.control" -> Text.literal("R-CTRL");
            case "key.keyboard.space" -> Text.literal("SPACE");
            case "key.keyboard.enter" -> Text.literal("ENTER");
            case "key.keyboard.tab" -> Text.literal("TAB");
            case "key.keyboard.escape" -> Text.literal("ESC");
            case "key.keyboard.backspace" -> Text.literal("BACKSPACE");
            case "key.keyboard.caps.lock" -> Text.literal("CAPS LOCK");
            case "key.keyboard.grave.accent" -> Text.literal("~");

            case "key.mouse.left" -> Text.literal("LMB");
            case "key.mouse.right" -> Text.literal("RMB");
            case "key.mouse.middle" -> Text.literal("MMB");

            default -> Text.translatable(translationKey);
        };
    }

    @SuppressWarnings("deprecation")
    public static Screen createScreen(Screen parent) {
        SearchifyPreviewImage.currentMode = SearchifyConfig.displayMode;
        SearchifyPreviewImage.currentSpeed = SearchifyConfig.animationSpeed;
        SearchifyPreviewImage.currentColor = SearchifyConfig.highlightColor;
        SearchifyPreviewImage.currentGhostAlpha = SearchifyConfig.ghostAlpha;
        SearchifyPreviewImage.currentPulseScale = SearchifyConfig.pulseScale;

        SearchifyPreviewImage previewImage = new SearchifyPreviewImage();

        ItemStack[] containerItems = new ItemStack[]{
                Items.RED_SHULKER_BOX.getDefaultStack(),
                Items.DIAMOND.getDefaultStack(),
                Items.RED_BUNDLE.getDefaultStack(),
                Items.IRON_INGOT.getDefaultStack(),
                Items.APPLE.getDefaultStack(),
                Items.BLUE_SHULKER_BOX.getDefaultStack(),
                Items.STICK.getDefaultStack(),
                Items.BLUE_BUNDLE.getDefaultStack()
        };
        SearchifyPreviewImage containerPreview = new SearchifyPreviewImage(containerItems, "shulker", "bundle");

        Text keyNameText = getCleanKeyName(SearchifyConfig.searchKeybind).copy().formatted(Formatting.YELLOW);

        ButtonOption keybindButton = ButtonOption.createBuilder()
                .name(Text.translatable("searchify.config.keybind.name"))
                .description(OptionDescription.of(Text.translatable("searchify.config.keybind.desc")))
                .text(Text.literal("[ ").append(keyNameText).append(Text.literal(" ]").formatted(Formatting.RESET)))
                .action((yaclScreen, opt) -> MinecraftClient.getInstance().setScreen(new KeybindCaptureScreen(yaclScreen)))
                .build();

        Option<Color> colorOption = Option.<Color>createBuilder()
                .name(Text.translatable("searchify.config.highlightColor.name"))
                .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.highlightColor.desc")).customImage(previewImage).build())
                .binding(new Color(0x00FF00), () -> new Color(SearchifyConfig.highlightColor), val -> SearchifyConfig.highlightColor = val.getRGB() & 0xFFFFFF)
                .available(SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.COLOR || SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.OUTLINE)
                .listener((opt, val) -> SearchifyPreviewImage.currentColor = val.getRGB() & 0xFFFFFF)
                .controller(ColorControllerBuilder::create).build();

        Option<Integer> animationSpeedOption = Option.<Integer>createBuilder()
                .name(Text.translatable("searchify.config.animationSpeed.name"))
                .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.animationSpeed.desc")).customImage(previewImage).build())
                .binding(100, () -> SearchifyConfig.animationSpeed, val -> SearchifyConfig.animationSpeed = val)
                .available(SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.ANIMATION || SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE)
                .listener((opt, val) -> SearchifyPreviewImage.currentSpeed = val)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(10, 250)
                        .step(1)
                        .formatValue(v -> Text.literal(v + "%"))).build();

        Option<Integer> pulseScaleOption = Option.<Integer>createBuilder()
                .name(Text.translatable("searchify.config.pulseScale.name"))
                .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.pulseScale.desc")).customImage(previewImage).build())
                .binding(140, () -> SearchifyConfig.pulseScale, val -> SearchifyConfig.pulseScale = val)
                .available(SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.PULSE)
                .listener((opt, val) -> SearchifyPreviewImage.currentPulseScale = val)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(100, 200).step(1).formatValue(v -> Text.literal(v + "%"))).build();

        Option<Integer> ghostAlphaOption = Option.<Integer>createBuilder()
                .name(Text.translatable("searchify.config.ghostAlpha.name"))
                .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.ghostAlpha.desc")).customImage(previewImage).build())
                .binding(70, () -> SearchifyConfig.ghostAlpha, val -> SearchifyConfig.ghostAlpha = val)
                .available(SearchifyConfig.displayMode == SearchifyConfig.DisplayMode.GHOST)
                .listener((opt, val) -> SearchifyPreviewImage.currentGhostAlpha = val)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(1).formatValue(v -> Text.literal(v + "%"))).build();

        Option<SearchifyConfig.DisplayMode> displayModeOption = Option.<SearchifyConfig.DisplayMode>createBuilder()
                .name(Text.translatable("searchify.config.displayMode.name"))
                .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.displayMode.desc")).customImage(previewImage).build())
                .binding(SearchifyConfig.DisplayMode.ANIMATION, () -> SearchifyConfig.displayMode, val -> SearchifyConfig.displayMode = val)
                .listener((opt, val) -> {
                    SearchifyPreviewImage.currentMode = val;
                    colorOption.setAvailable(val == SearchifyConfig.DisplayMode.COLOR || val == SearchifyConfig.DisplayMode.OUTLINE);
                    animationSpeedOption.setAvailable(val == SearchifyConfig.DisplayMode.ANIMATION || val == SearchifyConfig.DisplayMode.PULSE);
                    pulseScaleOption.setAvailable(val == SearchifyConfig.DisplayMode.PULSE);
                    ghostAlphaOption.setAvailable(val == SearchifyConfig.DisplayMode.GHOST);
                })
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(SearchifyConfig.DisplayMode.class)
                        .formatValue(v -> Text.translatable("searchify.config.enum." + v.name().toLowerCase()))).build();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.translatable("searchify.config.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Text.translatable("searchify.config.category.general"))
                        .tooltip(Text.translatable("searchify.config.category.general.tooltip"))

                        .group(OptionGroup.createBuilder()
                                .name(Text.translatable("searchify.config.group.behavior"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.translatable("searchify.config.enabled.name"))
                                        .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.enabled.desc")).build())
                                        .binding(true, () -> SearchifyConfig.isEnabled, val -> SearchifyConfig.isEnabled = val)
                                        .controller(TickBoxControllerBuilder::create).build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.translatable("searchify.config.autoLock.name"))
                                        .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.autoLock.desc"))
                                                .customImage(new IconPreviewImage(Identifier.of("searchify", "lock"))).build())
                                        .binding(false, () -> SearchifyConfig.autoLock, val -> {
                                            SearchifyConfig.autoLock = val;
                                            if (!val) {
                                                SearchifyConfig.savedSearchQuery = "";
                                            }
                                        })
                                        .controller(TickBoxControllerBuilder::create).build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.translatable("searchify.config.searchInside.name"))
                                        .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.searchInside.desc"))
                                                .customImage(containerPreview).build())
                                        .binding(true, () -> SearchifyConfig.searchInsideContainers, val -> SearchifyConfig.searchInsideContainers = val)
                                        .controller(TickBoxControllerBuilder::create).build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.translatable("searchify.config.enableHistory.name"))
                                        .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.enableHistory.desc"))
                                                .customImage(new IconPreviewImage(Identifier.of("searchify", "history"))).build())
                                        .binding(true, () -> SearchifyConfig.enableHistory, val -> SearchifyConfig.enableHistory = val)
                                        .controller(TickBoxControllerBuilder::create).build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.translatable("searchify.config.searchInPlayerInventory.name"))
                                        .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.searchInPlayerInventory.desc")).build())
                                        .binding(false, () -> SearchifyConfig.searchInPlayerInventory, val -> SearchifyConfig.searchInPlayerInventory = val)
                                        .controller(TickBoxControllerBuilder::create).build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.translatable("searchify.config.autoFocusSearchBar.name"))
                                        .description(OptionDescription.createBuilder().text(Text.translatable("searchify.config.autoFocusSearchBar.desc")).build())
                                        .binding(false, () -> SearchifyConfig.autoFocusSearchBar, val -> SearchifyConfig.autoFocusSearchBar = val)
                                        .controller(TickBoxControllerBuilder::create).build())
                                .option(keybindButton)
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Text.translatable("searchify.config.group.visuals"))
                                .option(displayModeOption)
                                .option(colorOption)
                                .option(animationSpeedOption)
                                .option(pulseScaleOption)
                                .option(ghostAlphaOption)
                                .build())
                        .build())

                .category(ConfigCategory.createBuilder()
                        .name(Text.translatable("searchify.config.category.guis"))
                        .tooltip(Text.translatable("searchify.config.category.guis.tooltip"))
                        .group(OptionGroup.createBuilder()
                                .name(Text.translatable("searchify.config.group.containers"))
                                .option(createContainerOption("block.minecraft.chest", "searchify.config.chest.desc", SearchifyConfig.enableChests, val -> SearchifyConfig.enableChests = val))
                                .option(createContainerOption("block.minecraft.barrel", "searchify.config.barrel.desc", SearchifyConfig.enableBarrels, val -> SearchifyConfig.enableBarrels = val))
                                .option(createContainerOption("block.minecraft.ender_chest", "searchify.config.enderChest.desc", SearchifyConfig.enableEnderChests, val -> SearchifyConfig.enableEnderChests = val))
                                .option(createContainerOption("block.minecraft.trapped_chest", "searchify.config.trappedChest.desc", SearchifyConfig.enableTrappedChests, val -> SearchifyConfig.enableTrappedChests = val))
                                .option(createContainerOption("block.minecraft.copper_chest", "searchify.config.copperChest.desc", SearchifyConfig.enableCopperChests, val -> SearchifyConfig.enableCopperChests = val))
                                .option(createContainerOption("block.minecraft.shulker_box", "searchify.config.shulkerBox.desc", SearchifyConfig.enableShulkerBoxes, val -> SearchifyConfig.enableShulkerBoxes = val))
                                .build())
                        .build())
                .save(SearchifyConfig::save)
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> createContainerOption(String nameKey, String descKey, boolean currentValue, java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Text.translatable(nameKey))
                .description(OptionDescription.of(Text.translatable(descKey)))
                .binding(true, () -> currentValue, setter)
                .controller(TickBoxControllerBuilder::create).build();
    }
}