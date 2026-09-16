package com.automine.AutoMineMod.client.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class AiMemory {
    private static final int MAX_EVENTS = 12;
    private static final int MAX_SAME = 3;

    private final Deque<String> events = new ArrayDeque<>();
    private final Deque<String> recentTypes = new ArrayDeque<>();

    public synchronized void record(AiIntent intent, String result) {
        String type = intent.type().name().toLowerCase(Locale.ROOT);
        events.addLast("- " + type + ": " + intent.reason() + " -> " + result);
        while (events.size() > MAX_EVENTS) events.removeFirst();
        recentTypes.addLast(type);
        while (recentTypes.size() > MAX_SAME + 2) recentTypes.removeFirst();
    }

    public synchronized String summary() {
        if (events.isEmpty()) return "(vazia)";
        return String.join("\n", events);
    }

    public synchronized String repetitionWarning() {
        if (recentTypes.size() < MAX_SAME) return null;
        String last = null;
        int streak = 0;
        for (String type : recentTypes) {
            if (type.equals(last)) streak++;
            else {
                last = type;
                streak = 1;
            }
        }
        if (streak >= MAX_SAME) {
            return "A intencao " + last + " ja foi repetida " + streak
                    + " vezes. Mude de estrategia se nao houve progresso.";
        }
        return null;
    }

    public synchronized void clear() {
        events.clear();
        recentTypes.clear();
    }
}
