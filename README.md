# AutoMine Mod

Mod de autoplay estilo RPG para Minecraft 26.2 com Fabric API. O personagem explora o mundo sozinho: procura e minera minérios, corta madeira, crafteia ferramentas e tochas, explora cavernas, nada em rios e mares, caça animais para comer e se defende de monstros. Com a API do Google AI (Gemini), ele passa a escolher sozinho o que fazer a cada poucos segundos — combate, coleta, exploração, fuga ou espera — no estilo do projeto [Minecraft_AI](https://github.com/Mai-xiyu/Minecraft_AI), mas rodando dentro do cliente Fabric.

## Funcionalidades

- **Explorador de Mundo**: procura e minera minérios, corta madeira, caça animais passivos e coleta a carne
- **Mineração Autônoma**: seleção automática da melhor ferramenta para cada bloco (picareta, machado, pá, enxada)
- **Craft Automático**: tat não spawna mais monstros para o jogador — tudo que cai vem de mobs naturais ou do ambiente
- **Cavernas**: desce escadas, coloca tochas, usa farelos de pão para voltar à superfície quando se perde
- **Natação**: cruza rios e lagos, mergulha atrás de minérios aquáticos e sobe para respirar
- **Combate Automático**: se defende de monstros hostis e mantém distância de creepers
- **Sobrevivência**: usa comida disponível na hotbar; se não houver, caça animais (vaca, porco, ovelha, galinha, coelho, mooshroom)
- **Movimento Inteligente**: navega com pulos, quebra-gelo de quedas, desvios e recuperação de entalos
- **Sistema de Toggle**: Ativa/desativa o autoplay com a tecla K (padrão)
- **Suporte Multiplayer**: Funciona em servidores e singleplayer
- **Agente Gemini**: decide intenções de alto nível (explorar, lutar, comer, fugir, ir até um ponto, coletar bloco, conversar)
- **Reflexos locais**: lava, queda, creeper e fome crítica continuam no motor do mod, mesmo se a API falhar
- **Memória curta**: o modelo vê o resultado da última ação e evita repetir estratégia sem progresso

## Requisitos

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API 0.158.0+26.2+
- Java 25+

## Compilação

### Pré-requisitos
- JDK 25
- Gradle (ou use o wrapper incluído)

### Comandos de Compilação

```powershell
# Windows (PowerShell)
.\gradlew.bat build

# Linux/Mac
./gradlew build
```

O arquivo JAR será gerado em `build/libs/`.

## Instalação

1. **Compile o mod**:
   ```powershell
   .\gradlew.bat build
   ```

2. **Copie o JAR**:
   - O arquivo compilado estará em `build/libs/automine-1.1.0.jar`
   - Copie este arquivo para a pasta `mods` da sua instalação Fabric

3. **Instale as dependências**:
   - Instale o [Fabric Loader 0.19.3+](https://fabricmc.net/use/) para Minecraft 26.2
   - Instale o [Fabric API 0.158.0+26.2](https://modrinth.com/mod/fabric-api)

4. **Inicie o jogo**:
   - O mod será carregado automaticamente
   - No jogo, pressione a tecla **J** para ativar/desativar o AutoPlay

## Configuração

### Controles Padrão
- **Tecla J**: Ativar/Desativar AutoPlay
- **Chat `!ai ...`**: define a tarefa atual da IA (não envia a mensagem ao servidor)

### Agente Google AI (Gemini)

1. Crie uma chave em [Google AI Studio](https://aistudio.google.com/apikey)
2. Defina a variável de ambiente `GOOGLE_AI_API_KEY` **ou** edite `config/automine-ai.json` na pasta do jogo:

```json
{
  "enabled": true,
  "apiKey": "",
  "model": "gemini-2.5-flash",
  "baseUrl": "https://generativelanguage.googleapis.com/v1beta",
  "temperature": 0.7,
  "maxTokens": 512,
  "decisionIntervalTicks": 40,
  "task": "Sobreviver, explorar o mundo e evoluir de forma autonoma.",
  "useCache": true
}
```

A variável de ambiente tem prioridade sobre o arquivo. Sem chave, o autoplay scriptado continua funcionando — agora como explorador autônomo (minera, crafteia, nada e se defende); a caça agressiva em raio amplo fica restrita ao intento `combat` da IA.

A barra de ação no jogo mostra o status da IA. Depois de 5 erros seguidos da API, a IA pausa e o comportamento local assume.

### Configurações Avançadas
Você pode modificar os seguintes valores nos arquivos do mod:

- `Targeting.TARGET_RADIUS_HORIZONTAL`: Raio horizontal de busca por ameaças (padrão: 48.0 blocos)
- `Targeting.TARGET_RANGE_VERTICAL`: Faixa vertical de busca, acima/abaixo do jogador (padrão: 6.0 blocos)
- `ExplorationController`: constantes de comportamento — distâncias de caça/mineração, ticks de ar submerso, cooldowns de tochas e monitor de descida (`HUNT_HUNGER`, `MAX_AIR_TICKS`, `TORCH_COOLDOWN`, `STAIR_STOP_DEPTH`, etc.)
- `AutoCraftManager`: metas de itens automáticos — `PLANKS_TARGET`, `STICK_TARGET`, `TORCH_TARGET`, intervalo de verificação (`INTERVAL`)
- `ATTACK_RANGE` em `ClientAutoPlayManager`: alcance de ataque (padrão: ~3.2 blocos)
- `ATTACK_COOLDOWN`: Cooldown entre ataques em ticks (padrão: 10 ticks = 0.5 segundos)

## Uso

1. Entre no mundo do Minecraft
2. Pressione a tecla **J** para ativar o AutoPlay
3. O personagem começará a explorar, minerar, caçar, nadar e se defender automaticamente
4. Pressione **K** novamente para desativar

## Comportamento

- O mod nunca mais gera monstros artificiais; o que cai vem de mobs naturais do mundo
- Busca minérios (carvão, ferro, cobre, ouro, redstone, lápis-lazúli, diamante, esmeralda) e madeira perto do jogador
- Minerar o bloco escolhido com a ferramenta correta selecionada automaticamente
- Sem combate: se não achar recursos, cava escadas para descer, coloca tochas e explora cavernas
- Em água: cruza rios/lagos, mergulha atrás de minérios e volta à superfície para respirar
- Fome baixa: se tem comida, come; senão, caça animais passivos e coleta a carne caída
- Mantém distância de creepers, evita lava e quedas grandes, e recua quando encurralado

## Desenvolvimento

### Estrutura do Projeto
```
automine/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/automine/AutoMineMod/
│   │   │       ├── AutoMineMod.java (classe principal)
│   │   │       ├── AutoPlayManager.java (tracking de alvos e loot natural)
│   │   │       ├── AutoCraftManager.java (craft automático de itens/ferramentas)
│   │   │       └── navigation/
│   │   │           ├── ResourceScanner.java (minérios, madeira, cavernas, água)
│   │   │           ├── ToolSelector.java (escolha de ferramenta por tier/recompensa)
│   │   │           ├── TerrainScanner.java (navegação e segurança do terreno)
│   │   │           ├── Targeting.java (alvos e ameaças)
│   │   │           ├── HumanMovement.java (comportamento humano)
│   │   │           ├── CameraSteering.java (câmera)
│   │   │           └── StepResult.java (resultado de passos)
│   │   └── resources/
│   │       ├── fabric.mod.json (metadados do mod)
│   │       ├── automine.mixins.json (configuração de mixins)
│   │       └── assets/
│   │           └── automine/
│   │               └── lang/
│   │                   ├── en_us.json (inglês)
│   │                   └── pt_br.json (português)
│   └── client/
│       └── java/
│           └── com/automine/AutoMineMod/client/
│               ├── AutoMineModClient.java (keybinds)
│               ├── ClientAutoPlayManager.java (movimento, combate, mineração, nado)
│               ├── ExplorationController.java (prioridades do explorador autônomo)
│               └── ai/ (agente Gemini: intentos, estado do mundo e prompts)
├── build.gradle (configuração do Gradle)
├── gradle.properties (propriedades do projeto)
└── settings.gradle (configuração do Gradle)
```

### Migração para 26.2

Este mod foi atualizado de Minecraft 1.21 (Yarn) para 26.2 (mappings oficiais Mojang). Principais mudanças:

- Fabric Loom 1.17 com plugin `net.fabricmc.fabric-loom`
- Sem Yarn mappings (jogo desofuscado)
- Java 25 e Gradle 9.5.1
- Source sets separados para código client (`src/client/java`)
- API renomeada: `KeyBinding` → `KeyMapping`, `getWorld()` → `level()`, etc.

## Licença

MIT License

## Créditos

Desenvolvido para a comunidade Minecraft Fabric
