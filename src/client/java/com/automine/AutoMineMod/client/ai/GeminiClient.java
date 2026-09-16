package com.automine.AutoMineMod.client.ai;

import com.automine.AutoMineMod.AutoMineMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class GeminiClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();
    private final AiConfig config;

    public GeminiClient(AiConfig config) {
        this.config = config;
    }

    public String generate(String systemPrompt, String userMessage) throws Exception {
        JsonObject root = new JsonObject();

        JsonObject system = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysText = new JsonObject();
        sysText.addProperty("text", systemPrompt);
        sysParts.add(sysText);
        system.add("parts", sysParts);
        root.add("systemInstruction", system);

        JsonArray contents = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", userMessage);
        parts.add(part);
        user.add("parts", parts);
        contents.add(user);
        root.add("contents", contents);

        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", config.temperature);
        gen.addProperty("maxOutputTokens", config.maxTokens);
        gen.addProperty("responseMimeType", "application/json");
        root.add("generationConfig", gen);

        String url = config.baseUrl.replaceAll("/$", "")
                + "/models/" + config.model + ":generateContent";
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", config.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code == 429 || code >= 500) {
                    Thread.sleep(400L * (1L << attempt));
                    continue;
                }
                if (code >= 400) {
                    throw new IllegalStateException("Gemini HTTP " + code + ": " + truncate(response.body()));
                }
                return extractText(response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                last = e;
                AutoMineMod.LOGGER.debug("Gemini tentativa {} falhou: {}", attempt + 1, e.getMessage());
                if (attempt == 2) break;
                Thread.sleep(400L * (1L << attempt));
            }
        }
        throw last != null ? last : new IllegalStateException("Gemini falhou sem resposta");
    }

    private static String extractText(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        JsonArray candidates = json.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini sem candidatos: " + truncate(body));
        }
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        JsonArray parts = content.getAsJsonArray("parts");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            JsonObject part = parts.get(i).getAsJsonObject();
            if (part.has("text")) text.append(part.get("text").getAsString());
        }
        String result = text.toString().trim();
        if (result.isEmpty()) throw new IllegalStateException("Gemini respondeu vazio");
        return result;
    }

    private static String truncate(String body) {
        if (body == null) return "";
        return body.length() > 240 ? body.substring(0, 240) : body;
    }
}
