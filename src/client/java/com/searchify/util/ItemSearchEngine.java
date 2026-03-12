package com.searchify.util;

import com.searchify.client.SearchifyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.List;

public class ItemSearchEngine {

    public static boolean matches(ItemStack stack, String query) {
        if (stack.isEmpty() || query.isEmpty()) return false;
        return performDeepSearch(stack, query, 0);
    }

    private static boolean performDeepSearch(ItemStack stack, String query, int depth) {
        if (depth > 3 || stack.isEmpty()) return false;
        if (matchesSelf(stack, query)) return true;

        if (SearchifyConfig.searchInsideContainers) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                for (ItemStack innerStack : container.iterateNonEmpty()) {
                    if (performDeepSearch(innerStack, query, depth + 1)) return true;
                }
            }

            BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundle != null) {
                for (ItemStack innerStack : bundle.iterate()) {
                    if (performDeepSearch(innerStack, query, depth + 1)) return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesSelf(ItemStack stack, String query) {
        // Tag query
        if (query.startsWith("#")) {
            String modQuery = query.substring(1).trim();
            return modQuery.isEmpty() || Registries.ITEM.getId(stack.getItem()).getNamespace().contains(modQuery);
        }

        // Deep Tag query
        if (query.startsWith("$")) {
            String tagQuery = query.substring(1).trim();
            if (tagQuery.isEmpty()) return true;
            return stack.streamTags().anyMatch(tag ->
                    tag.id().getPath().contains(tagQuery) || tag.id().getPath().replace('_', ' ').contains(tagQuery)
            );
        }

        // Tooltip or Enchantment query
        if (query.startsWith("@")) {
            String tooltipQuery = query.substring(1).trim();
            return tooltipQuery.isEmpty() || searchInTooltip(stack, tooltipQuery) || searchInEnchantments(stack, tooltipQuery);
        }

        // Basic Name and ID search
        if (stack.getName().getString().toLowerCase().contains(query)) return true;
        String itemId = Registries.ITEM.getId(stack.getItem()).getPath();
        if (itemId.contains(query) || itemId.replace('_', ' ').contains(query)) return true;

        return searchInTooltip(stack, query) || searchInEnchantments(stack, query);
    }

    private static boolean searchInTooltip(ItemStack stack, String query) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return false;
        try {
            List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
            for (Text line : tooltip) {
                if (line.getString().toLowerCase().contains(query)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean searchInEnchantments(ItemStack stack, String query) {
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null && checkEnchantments(enchantments, query)) return true;

        ItemEnchantmentsComponent storedEnchantments = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        return storedEnchantments != null && checkEnchantments(storedEnchantments, query);
    }

    private static boolean checkEnchantments(ItemEnchantmentsComponent component, String query) {
        return component.getEnchantments().stream()
                .filter(entry -> entry.getKey().isPresent())
                .anyMatch(entry -> {
                    String id = entry.getKey().get().getValue().getPath();
                    return id.contains(query) || id.replace('_', ' ').contains(query);
                });
    }
}