# AutoMine Mod

Mod de autoplay estilo RPG idle para Minecraft 26.3 com Fabric API. O personagem explora, se alimenta e combate monstros sozinho.

## Funcionalidades

- **Combate Automático**: O personagem detecta monstros hostis em um raio de 32 blocos
- **Movimento Inteligente**: Move-se automaticamente em direção aos monstros
- **Ataque Automático**: Ataca monstros quando estiver em alcance
- **Sobrevivência**: Usa comida disponível na hotbar quando a fome baixa
- **Sobrevivência**: Prioriza segurança, alimentação e combate antes da exploração
- **Exploração**: Escolhe direções seguras e continua andando quando a área está tranquila
- **Sistema de Toggle**: Ativa/desativa o autoplay com a tecla K (padrão)
- **Suporte Multiplayer**: Funciona em servidores e singleplayer

## Requisitos

- Minecraft 26.3
- Fabric Loader 0.19.5+
- Fabric API 0.165.0+26.3+
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
   - Instale o [Fabric Loader 0.19.5+](https://fabricmc.net/use/) para Minecraft 26.3
   - Instale o [Fabric API 0.165.0+26.3](https://modrinth.com/mod/fabric-api)

4. **Inicie o jogo**:
   - O mod será carregado automaticamente
   - No jogo, pressione a tecla **K** para ativar/desativar o AutoPlay

## Configuração

### Controles Padrão
- **Tecla K**: Ativar/Desativar AutoPlay

### Configurações Avançadas
Você pode modificar os seguintes valores no arquivo `AutoPlayManager.java`:

- `Targeting.TARGET_RADIUS_HORIZONTAL`: Raio horizontal de busca por monstros (padrão: 48.0 blocos)
- `Targeting.TARGET_RANGE_VERTICAL`: Faixa vertical de busca, acima/abaixo do jogador (padrão: 6.0 blocos) — evita engajar alvos no subsolo
- `ATTACK_RANGE`: Alcance de ataque (padrão: 4.0 blocos)
- `ATTACK_COOLDOWN`: Cooldown entre ataques em ticks (padrão: 10 ticks = 0.5 segundos)

## Uso

1. Entre no mundo do Minecraft
2. Pressione a tecla **K** para ativar o AutoPlay
3. O personagem começará a explorar, comer e atacar monstros automaticamente
4. Pressione **K** novamente para desativar

## Comportamento

- O mod detecta automaticamente monstros hostis (zumbis, esqueletos, etc.)
- Move-se em direção ao monstro mais próximo
- Ataca quando estiver em alcance
- Mantém distância de creepers e evita quedas em buracos
- Para automaticamente se não houver monstros nas proximidades

## Desenvolvimento

### Estrutura do Projeto
```
automine/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/automine/AutoMineMod/
│   │   │       ├── AutoMineMod.java (classe principal)
│   │   │       └── AutoPlayManager.java (lógica de combate)
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
│               └── AutoMineModClient.java (keybinds)
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
