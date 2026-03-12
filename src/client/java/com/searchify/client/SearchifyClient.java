package com.searchify.client;

import net.fabricmc.api.ClientModInitializer;

public class SearchifyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SearchifyConfig.load();
    }
}