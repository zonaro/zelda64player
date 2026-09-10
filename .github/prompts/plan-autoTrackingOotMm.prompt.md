# Auto-tracking OoT/MM — plano refinado e implementação

## Decisões finais

- Toggle **global**, `CorePrefs.PREF_TRACKER_AUTO_TRACKING`, desligado por padrão. O Switch no tracker e a opção no Dashboard usam os mesmos getters/setters. Não há preferência por hack nem uma segunda configuração no JSON.
- Progresso continua separado por hackId (incluindo `vanilla_<crc32>`). Manual permanece habilitado. Auto só acrescenta itens, aumenta níveis e aprende músicas; não remove progresso, checks, dicas ou tempo.
- Escopo de memória: OoT NTSC 1.0 (`0x11A5D0`) e MM US 1.0 (`0x1EF670`), além de hacks/randomizers que preservem esses layouts. Não prometer compatibilidade universal ou "99%": assinatura e invariantes precisam passar. CRC32 não é fallback para um buffer curto.
- Sem ROMs de terceiros, sockets, cópia integral da RDRAM, novo JNI ou alterações nos controles.

## Correções da análise

1. `getMemoryRegion` adquire `coreLock`, um `std::mutex` não recursivo. Chamá-lo no callback, que já segura esse lock, causa deadlock. Obter o alias após o primeiro frame, fora do callback, como RA; ler apenas no callback. Remover o callback (sincronizado pelo mesmo lock) antes de soltar o alias e destruir o core. Somente snapshots de escalares atravessam para Main.
2. A RDRAM do núcleo não pode ser interpretada apenas com `ByteOrder.BIG_ENDIAN`: os bytes podem estar invertidos dentro de palavras de 32 bits. Detectar a ordem com `ZELDAZ`/`ZELDA3`; acessar bytes absolutos por lane XOR 0, 3 ou 1, sem alterar posição/order do buffer.
3. MM possui 48 entradas (24 itens + 24 máscaras), inventário `+0x70`, equipamento `+0x6C`, upgrades `+0xB8` e quest flags `+0xBC`. OoT usa 24 entradas em `+0x74`, equipamento possuído `+0x9C`, upgrades `+0xA0`, quests `+0xA4`.
4. Zelda's Lullaby é bit 12 em OoT; bit 18 representa Kokiri Emerald. IDs e músicas de MM têm tabelas próprias. Biggoron exige seu flag permanente, pois o bit da espada também representa Giant's Knife.
5. Uma assinatura válida, capacidade de vida plausível e modo normal são necessários. Inventário vazio é válido; não exigir um item para começar. Buffers curtos, offsets inválidos e tela de título são ignorados.
6. Usar relógio monotônico com intervalo de 100 ms, não módulo de frames: mantém o limite de 10 Hz em fast-forward e diferentes taxas de emulação.
7. Gameplay e diálogo compartilham o estado vivo do repositório. Sem isso, um diálogo aberto poderia sobrescrever descobertas com uma cópia antiga. As tabs observam revisões e atualizam as células existentes, preservando foco/rolagem.

## Implementação

- `tracker/autotracker/model/AutoTrackerSnapshot.kt`: cópia imutável dos campos necessários, sem alias de RAM.
- `tracker/autotracker/parser/SaveContextParser.kt`: offsets por jogo, assinatura, byte lanes, bounds e invariantes.
- `tracker/autotracker/mapper/AutoTrackerMapper.kt`: IDs compatíveis com os catálogos atuais, upgrades, equipamento, máscaras, músicas, medalhões/remains; merge aditivo idempotente.
- `tracker/autotracker/AutoTrackerPoller.kt`: throttle e emissão só quando o snapshot muda; Mudanças no toggle invalidam o diff, inclusive OFF/ON entre frames.
- `GameActivityViewModel`: um callback composto RA + tracker, independente do login/ativação de RA; geração de sessão rejeita entregas após teardown. Persistência fora da thread GL, no Main.
- `TrackerRepository`/`TrackerViewModel`: estado vivo compartilhado e fluxo de revisão; persistência JSON existente por hack preservada.
- `TrackerDialogFragment`, `tracker_dialog.xml`: Switch com accent e foco existentes, sincronizado com mudanças externas de preferência.
- `SettingsRoutes` e três JSONs do Dashboard: paridade global.
- Strings pt-BR/en/es; controles manuais continuam utilizáveis.

## Semântica de edição manual

O diff é por snapshot. Uma correção manual permanece enquanto a RAM não muda; quando uma nova fotografia chega, os itens nela presentes podem ser novamente acrescentados. OFF/ON também reaplica a fotografia atual. Trocas de save/ciclos de MM não apagam descobertas anteriores. Isso é um histórico aditivo, não um espelho exato do inventário atual.

## Verificação

Testes sintéticos (sem ROM): byte lanes, posição/order preservados, offsets distintos de MM, máscaras/equipamentos/magia, assinatura/bounds/título inválidos, inventário vazio, IDs desconhecidos, merge aditivo/idempotente, separação por jogo, compatibilidade com IDs/limites dos catálogos, throttle/diff/OFF-ON.

Comandos: `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` e build release para QA em aparelho. O projeto atual usa **JUnit 4**, não JUnit 5/MockK.

QA em aparelho deve distinguir: abrir diálogo/toggle, fechamento/reabertura, marcação manual e persistência; execução real de OoT/MM; aquisição de item e seed randomizer. Compilação e fixtures não comprovam aquisição em ROM real. Registrar os cenários executados e os indisponíveis no relatório final.

## Referências verificadas

- [OoT SaveContext](https://github.com/zeldaret/oot/blob/main/include/save.h)
- [MM SaveContext](https://github.com/zeldaret/mm/blob/main/include/z64save.h)
- [MM item IDs](https://github.com/zeldaret/mm/blob/main/include/z64item.h)
- [Mupen libretro memory API](https://github.com/libretro/mupen64plus-libretro-nx/blob/develop/libretro/libretro.c)
- Implementação vendorizada: `libretrodroid/src/main/cpp/libretrodroid.cpp` (`getMemoryRegion`, `setFrameCallback`, `step`), `RaSessionManager` e teardown de `GameActivity`.


## Resultado da execução

Implementação integrada e fixtures concluídas. Suíte: 307 testes, 8 deles novos para auto-tracking/repositório; builds debug e release passaram. APK final em `app/build/outputs/apk/release/app-release.apk`. QA visual, coleta real de itens e seeds randomizer pendentes: a conexão ADB com SM-A055M caiu após uma instalação inicial e retornou `No route to host`. Na retomada, o release `26.253.0647` (`262530647`) foi instalado via USB no SM-A055M com sucesso, preservando os dados; a versão instalada e o processo iniciado foram confirmados via ADB. A coleta real de itens continua pendente.
