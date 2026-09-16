package com.automine.AutoMineMod.client.ai;

import com.automine.AutoMineMod.AutoMineMod;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutonomousAgent {
    private final AiConfig config;
    private final GeminiClient gemini;
    private final AiMemory memory = new AiMemory();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "automine-gemini");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<AiIntent> pending = new ConcurrentLinkedQueue<>();
    private final List<String> recentChats = new ArrayList<>();
    private final Map<String, AiIntent> cache = new ConcurrentHashMap<>();

    private volatile String lastResult = "inicio";
    private volatile String statusLine = "IA ociosa";
    private volatile boolean paused;
    private int consecutiveErrors;
    private AiIntent activeIntent = AiIntent.idle("aguardando primeira decisao");

    public AutonomousAgent(AiConfig config) {
        this.config = config;
        this.gemini = new GeminiClient(config);
        if (!config.hasApiKey()) {
            statusLine = "IA desligada: defina GOOGLE_AI_API_KEY ou config/automine-ai.json";
            AutoMineMod.LOGGER.warn(statusLine);
        } else {
            statusLine = "IA Gemini pronta (" + config.model + ")";
        }
    }

    public boolean isEnabled() {
        return config.enabled && config.hasApiKey() && !paused;
    }

    public AiConfig config() {
        return config;
    }

    public String statusLine() {
        return statusLine;
    }

    public AiIntent activeIntent() {
        return activeIntent;
    }

    public void addChat(String line) {
        synchronized (recentChats) {
            recentChats.add(line);
            while (recentChats.size() > 6) recentChats.removeFirst();
        }
    }

    public void setTask(String task) {
        config.task = task;
        memory.clear();
        statusLine = "Nova tarefa: " + task;
    }

    public void noteResult(String result) {
        lastResult = result;
        if (activeIntent != null) memory.record(activeIntent, result);
    }

    public AiIntent pollIntent() {
        AiIntent next = pending.poll();
        if (next != null) activeIntent = next;
        return next;
    }

    public void requestDecision(LocalPlayer player) {
        if (!isEnabled() || player == null || !busy.compareAndSet(false, true)) return;
        List<String> chats;
        synchronized (recentChats) {
            chats = new ArrayList<>(recentChats);
        }
        String state = WorldStateCollector.collect(player, lastResult, chats);
        String warning = memory.repetitionWarning();
        String cacheKey = Integer.toHexString(state.hashCode());
        worker.submit(() -> decide(state, warning, cacheKey));
    }

    private void decide(String state, String warning, String cacheKey) {
        try {
            if (config.useCache && cache.containsKey(cacheKey) && warning == null) {
                AiIntent cached = cache.get(cacheKey);
                pending.offer(cached);
                statusLine = "IA cache: " + cached.type().name().toLowerCase();
                consecutiveErrors = 0;
                return;
            }
            String user = AiPrompts.formatUserMessage(config.task, state, memory.summary(), warning);
            String raw = gemini.generate(AiPrompts.SYSTEM, user);
            AiIntent intent = AiIntent.parse(raw);
            if (config.useCache) {
                cache.put(cacheKey, intent);
                if (cache.size() > 48) cache.clear();
            }
            pending.offer(intent);
            statusLine = "IA: " + intent.type().name().toLowerCase() + " — " + intent.reason();
            consecutiveErrors = 0;
            AutoMineMod.LOGGER.info("Intencao Gemini: {}", intent.rawJson());
        } catch (Exception e) {
            consecutiveErrors++;
            statusLine = "IA erro: " + e.getMessage();
            AutoMineMod.LOGGER.warn("Falha na decisao Gemini: {}", e.getMessage());
            if (consecutiveErrors >= 5) {
                paused = true;
                statusLine = "IA pausada apos 5 erros. Sem chave/rede, o autoplay scriptado continua.";
            }
            pending.offer(AiIntent.idle("fallback apos erro da IA"));
        } finally {
            busy.set(false);
        }
    }

    public void shutdown() {
        worker.shutdownNow();
    }
}
