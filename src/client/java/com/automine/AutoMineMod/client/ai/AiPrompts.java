package com.automine.AutoMineMod.client.ai;

public final class AiPrompts {
    private AiPrompts() {
    }

    public static final String SYSTEM = """
            Voce e um personagem autonomo de Minecraft. Decida a PROXIMA intencao de alto nivel.
            O movimento fino, pulos, mira e combate corpo-a-corpo ja sao executados pelo motor do mod.
            Responda SOMENTE um JSON valido, sem markdown.

            Formato:
            {"type":"explore|combat|eat|flee|wait|look_around|go_to|collect|chat","reason":"motivo curto","x":0,"y":64,"z":0,"yaw":90,"blockType":"oak_log","message":"oi","holdTicks":40}

            Regras:
            1. Sobrevivencia primeiro: vida baixa, fome baixa, lava, queda, creeper perto -> eat/flee.
            2. Ameaca hostil visivel e voce esta armado -> combat.
            3. Sem ameaca: explore, colete madeira/minerio util, ou va ate um ponto interessante.
            4. Nao repita a mesma acao se o ultimo resultado foi falha ou sem progresso.
            5. chat so para reportar progresso ou responder jogadores. Nao pergunte regras basicas do jogo.
            6. go_to exige x,y,z. collect exige blockType conhecido no estado.
            7. holdTicks entre 20 e 80 (ticks de jogo ate a proxima decisao).
            """;

    public static String formatUserMessage(String task, String worldState, String memory, String repeatWarning) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Tarefa\n").append(task).append("\n\n");
        sb.append(worldState);
        if (memory != null && !memory.isBlank()) {
            sb.append("\n\n## Memoria recente\n").append(memory);
        }
        if (repeatWarning != null && !repeatWarning.isBlank()) {
            sb.append("\n\n## Aviso\n").append(repeatWarning);
        }
        sb.append("\n\nEscolha a proxima intencao em JSON.");
        return sb.toString();
    }
}
