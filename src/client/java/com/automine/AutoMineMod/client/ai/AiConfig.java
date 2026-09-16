package com.automine.AutoMineMod.client.ai;

import com.automine.AutoMineMod.AutoMineMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public String apiKey = "";
    public String model = "gemini-2.5-flash";
    public String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    public double temperature = 0.7;
    public int maxTokens = 512;
    public int decisionIntervalTicks = 40;
    public String task = "Sobreviver, explorar o mundo e evoluir de forma autonoma como um jogador experiente.";
    public boolean useCache = true;

    private AiConfig() {
    }

    public static AiConfig load() {
        AiConfig config = new AiConfig();
        Path file = configPath();
        if (Files.isRegularFile(file)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (json.has("enabled")) config.enabled = json.get("enabled").getAsBoolean();
                if (json.has("apiKey")) config.apiKey = json.get("apiKey").getAsString();
                if (json.has("model")) config.model = json.get("model").getAsString();
                if (json.has("baseUrl")) config.baseUrl = json.get("baseUrl").getAsString();
                if (json.has("temperature")) config.temperature = json.get("temperature").getAsDouble();
                if (json.has("maxTokens")) config.maxTokens = json.get("maxTokens").getAsInt();
                if (json.has("decisionIntervalTicks")) {
                    config.decisionIntervalTicks = json.get("decisionIntervalTicks").getAsInt();
                }
                if (json.has("task")) config.task = json.get("task").getAsString();
                if (json.has("useCache")) config.useCache = json.get("useCache").getAsBoolean();
            } catch (Exception e) {
                AutoMineMod.LOGGER.warn("Falha ao ler {}: {}", file, e.getMessage());
            }
        } else {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
            } catch (IOException e) {
                AutoMineMod.LOGGER.warn("Nao foi possivel criar {}: {}", file, e.getMessage());
            }
        }
        String env = firstNonBlank(
                System.getenv("GOOGLE_AI_API_KEY"),
                System.getenv("GEMINI_API_KEY"),
                System.getProperty("automine.google.api.key"));
        if (env != null) config.apiKey = env;
        return config;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public static Path configPath() {
        Minecraft client = Minecraft.getInstance();
        Path dir = client != null ? client.gameDirectory.toPath() : Path.of(".");
        return dir.resolve("config").resolve("automine-ai.json");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
