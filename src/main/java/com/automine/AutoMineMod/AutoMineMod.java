package com.automine.AutoMineMod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
// networking receiver removed for now; keep server-side autoplay enabled for testing
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.resources.Identifier;

public class AutoMineMod implements ModInitializer {
    public static final String MOD_ID = "automine";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier TARGET_SYNC = Identifier.fromNamespaceAndPath(MOD_ID, "target_sync");

    public static boolean autoPlayEnabled = false;
    public static AutoPlayManager autoPlayManager;

    @Override
    public void onInitialize() {
        LOGGER.info("AutoMine Mod está carregando...");

        // register server tick handler
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // enable server-side autoplay by default for testing
        autoPlayEnabled = true;
        autoPlayManager = new AutoPlayManager();
        LOGGER.info("AutoPlay ativado por padrão para testes.");

        LOGGER.info("AutoMine Mod carregado com sucesso!");
    }

    private void onServerTick(MinecraftServer server) {
        if (autoPlayEnabled && autoPlayManager != null) {
            autoPlayManager.tick(server);
        }
    }

    public static void toggleAutoPlay(boolean enabled) {
        autoPlayEnabled = enabled;
        if (enabled) {
            LOGGER.info("AutoPlay ativado!");
        } else {
            LOGGER.info("AutoPlay desativado!");
        }
    }
}
