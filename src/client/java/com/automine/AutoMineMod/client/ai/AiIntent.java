package com.automine.AutoMineMod.client.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;

public record AiIntent(
        Type type,
        String reason,
        Double x,
        Double y,
        Double z,
        Float yaw,
        String blockType,
        String message,
        int holdTicks,
        String rawJson
) {
    public enum Type {
        EXPLORE, COMBAT, EAT, FLEE, WAIT, LOOK_AROUND, GO_TO, COLLECT, CHAT, IDLE
    }

    public static AiIntent idle(String reason) {
        return new AiIntent(Type.IDLE, reason, null, null, null, null, null, null, 20, "{}");
    }

    public BlockPos blockPos() {
        if (x == null || y == null || z == null) return null;
        return BlockPos.containing(x, y, z);
    }

    public static AiIntent parse(String response) {
        String json = extractJson(response);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        Type type = parseType(text(obj, "type"));
        String reason = text(obj, "reason");
        if (reason == null) reason = type.name().toLowerCase();
        int hold = obj.has("holdTicks") ? Math.max(10, obj.get("holdTicks").getAsInt()) : 40;
        return new AiIntent(
                type,
                reason,
                number(obj, "x"),
                number(obj, "y"),
                number(obj, "z"),
                obj.has("yaw") ? obj.get("yaw").getAsFloat() : null,
                text(obj, "blockType"),
                text(obj, "message"),
                hold,
                json
        );
    }

    private static Type parseType(String raw) {
        if (raw == null) return Type.EXPLORE;
        return switch (raw.trim().toLowerCase().replace("-", "_")) {
            case "combat", "attack", "fight" -> Type.COMBAT;
            case "eat", "food" -> Type.EAT;
            case "flee", "escape", "run" -> Type.FLEE;
            case "wait", "idle" -> Type.WAIT;
            case "look_around", "lookaround", "look" -> Type.LOOK_AROUND;
            case "go_to", "goto", "moveto", "move_to" -> Type.GO_TO;
            case "collect", "mine", "dig", "gather" -> Type.COLLECT;
            case "chat", "say" -> Type.CHAT;
            case "explore" -> Type.EXPLORE;
            default -> Type.EXPLORE;
        };
    }

    private static String extractJson(String response) {
        String trimmed = response.trim();
        int fence = trimmed.indexOf("```");
        if (fence >= 0) {
            int start = trimmed.indexOf('\n', fence);
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) trimmed = trimmed.substring(start + 1, end).trim();
        }
        int objStart = trimmed.indexOf('{');
        int objEnd = trimmed.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            return trimmed.substring(objStart, objEnd + 1);
        }
        throw new IllegalArgumentException("Resposta da IA sem JSON");
    }

    private static String text(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        JsonElement el = obj.get(key);
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static Double number(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }
}
