package com.automine.AutoMineMod.client;

import com.automine.AutoMineMod.AutoMineMod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class AutoMineModClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(AutoMineMod.MOD_ID, "general")
    );

    private static KeyMapping autoPlayKey;
    private static ClientAutoPlayManager clientAutoPlayManager;

    @Override
    public void onInitializeClient() {
        autoPlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.automine.toggle",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_J,
                CATEGORY
        ));

        // create client manager and enable autoplay client-side for testing visuals
        clientAutoPlayManager = new ClientAutoPlayManager();
        AutoMineMod.autoPlayEnabled = true;

        // server->client networking receiver removed temporarily

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (autoPlayKey.consumeClick()) {
                toggleAutoPlay();
            }
            // run client autoplay logic each tick (visual movement/animations)
            if (clientAutoPlayManager != null) {
                clientAutoPlayManager.tick(client);
            }
        });
    }

    private void toggleAutoPlay() {
        boolean newState = !AutoMineMod.autoPlayEnabled;
        AutoMineMod.toggleAutoPlay(newState);

        if (clientAutoPlayManager == null) {
            clientAutoPlayManager = new ClientAutoPlayManager();
        }

        if (newState) {
            AutoMineMod.LOGGER.info("AutoPlay ativado pelo jogador!");
        } else {
            clientAutoPlayManager.reset();
            if (AutoMineMod.autoPlayManager != null) AutoMineMod.autoPlayManager.reset();
            AutoMineMod.LOGGER.info("AutoPlay desativado pelo jogador!");
        }
    }
}
