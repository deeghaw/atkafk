package com.automine.AutoMineMod.client;

import com.automine.AutoMineMod.AutoMineMod;
import com.automine.AutoMineMod.client.ai.AiConfig;
import com.automine.AutoMineMod.client.ai.AutonomousAgent;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoMineModClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(AutoMineMod.MOD_ID, "general")
    );

    private static KeyMapping autoPlayKey;
    private static ClientAutoPlayManager clientAutoPlayManager;
    private static AutonomousAgent agent;

    @Override
    public void onInitializeClient() {
        autoPlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.automine.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));

        agent = new AutonomousAgent(AiConfig.load());
        clientAutoPlayManager = new ClientAutoPlayManager(agent);
        AutoMineMod.autoPlayEnabled = true;

        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, receptionTimestamp) -> {
            String name = sender != null ? sender.name() : "?";
            agent.addChat(name + ": " + message.getString());
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("!ai ")) {
                agent.setTask(message.substring(4).trim());
                AutoMineMod.LOGGER.info("Tarefa da IA atualizada pelo chat");
                return false;
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (autoPlayKey.consumeClick()) {
                toggleAutoPlay();
            }
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
