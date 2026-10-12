# Unstable Underworld v3.3.2 — Minecraft Java 26.3

## Hotfix de carga del mundo (crash / aviso de datapacks)

**Causa concreta detectada en v3.3.1:** la tabla de botín contenía
`minecraft:chain` (ID que fue reemplazado por `minecraft:iron_chain` en Minecraft 1.21.9).
Un JSON con un ID de objeto inexistente puede provocar errores durante la carga
de recursos y presentar la advertencia de datapacks / modo seguro.

**Cambio en v3.3.2:** `minecraft:chain` → `minecraft:iron_chain`.
Se mantiene intacto el resto de la tabla de botín, las estructuras, la geología,
las dimensiones y el comportamiento de portales. El validador ahora rechaza ese
identificador obsoleto antes del build.

**No se ha probado el arranque real del servidor**; esta es una corrección
fundamentada en el ID inválido encontrado, no una garantía de que no existan otros
errores de carga. Para confirmarlo usa los registros reales (`logs/latest.log`
y `crash-reports`).

### Actualización segura

1. Haz una **copia íntegra** de tu mundo, incluyendo `level.dat`, `DIM*` y
   `dimensions/` si existen; guarda también la versión anterior del JAR.
2. Compila este proyecto con **Actions → Build Minecraft 26.3 mod**.
3. Apaga el servidor y elimina el JAR anterior antes de instalar `unstable-underworld-3.3.2.jar`.
   Utiliza exactamente la misma versión en el cliente y el servidor.
4. Prueba primero en una **copia** del mundo. No uses el modo seguro para guardar
   el único original.
5. Si sigue mostrando el mensaje, adjunta el `latest.log` y los informes de cierre
   para identificar la entrada exacta que el cargador rechaza.

---

# Unstable Underworld v3.3.2 — Minecraft 26.3

**Hotfix of v3.3 for world/datapack loading.**

Minecraft 26.3 changed the JSON loot-table grammar. The original v3.3 resource `data/underworld/loot_table/chests/forgotten_relics.json` used the old `functions` / `function` keys, which can prevent a world from loading and trigger Safe Mode. The corrected loot table uses `modifier` / `type`. The build's static resource validator now checks for these legacy keys and fails early.

This does not modify Nether world generation, structures, terrain, dimensions, game modes, or portal behavior.

## Safe recovery

1. **Make a complete backup** of the server world and single-player save (including `level.dat` and all dimension folders) before further tests.
2. Do **not** use the Safe Mode option to save/overwrite your only copy.
3. Stop the server; remove the old `unstable-underworld-3.3.2.jar`, then install the newly compiled `unstable-underworld-3.3.2.jar` **on both the server and each Fabric client**, together with the matching dependencies.
4. Build with GitHub Actions (`Build Minecraft 26.3 mod`) and check that resource validation and Gradle succeed; then start a **copy** of the existing world.
5. If startup still fails, inspect `logs/latest.log` and `crash-reports/*.txt`, looking for `Failed to load datapacks`, `Couldn't parse data file`, or `Caused by:`. Provide those logs so a separate cause can be diagnosed.

**Limitation:** Passing the source validator is not proof the game accepts all datapack resources. No full 26.3 server startup test was run here.

---

# Unstable Underworld v3.3 — Minecraft Java 26.3 / Fabric

A separate Underworld dimension that you fall into from below the Nether. The original Nether terrain generator and existing Nether chunks remain untouched.

## New in v3.3

- **Eight handcrafted procedural ruin families**, including gothic gates, shrines, towers, citadels, mausoleums, shattered archives, fractured bridges, and lost markets.
- **No perfectly-aligned structure grid:** deterministic seed-based, rotated and jittered candidate locations, 30% deliberate empty cells, and neighbor spacing rejection. Existing v3.2 structure positions change once per upgraded Underworld chunk.
- **Loot chests** (68% primary chest + 24% independent secondary chest chance per generated ruin). The custom `underworld:chests/forgotten_relics` loot table mixes ancient-city/sculk finds and bastion/Nether treasures with mostly atmospheric low-to-mid rewards and rare valuable relics.
- **Very rare usable Overworld portals:** eligible kinds = gates and mausoleums; 1/110 chance *per eligible ruin*. Active violet portal blocks in black obsidian frames send entities to safe solid Overworld surfaces. They do not generate or modify Nether/Overworld blocks beyond destination selection.
- **Returning to the Nether via `/underworld return` or rising above Y=315 now puts entities at exact Y=-7, same X/Z.** This is deliberately below the Nether floor, so entities can fall back into the Underworld. Survival mode is restored on return.

## Previous behavior retained

- Minecraft **Java Edition 26.3**, Fabric Loader and Fabric API, Java 25 / Gradle 9.7.1.
- Independent Underworld dimension with Nether-type reddish sky, no sun, and no clouds.
- The player remains at original X/Z while falling from the Nether into the Underworld from Y=296; no spawn platform; downward velocity preserved.
- Automatic Adventure mode inside Underworld; restores saved mode upon leaving.
- The Underworld has about 30 blocks of thickness, shallow mountains, far-reaching soul sand/soul soil and blackstone regions, huge void chasms, and no land beyond radius 6,400 blocks.
- Falling out the *bottom* of the Underworld results in regular Minecraft void damage and death (never returns you to Nether).

## Commands

`/underworld enter`: fall into the dimension at your current X/Z coordinates.

`/underworld return`: return to Nether at Y=-7 (your X/Z are preserved).

## Building on GitHub

1. Upload the contents of this ZIP to the **root** of your repository (not inside an extra folder).
2. Go to **Actions → Build Minecraft 26.3 mod → Run workflow**.
3. Download the artifact and extract `unstable-underworld-3.3.2.jar`.
4. Install Fabric Loader and the matching Fabric API, and put this JAR into `mods/`.

GitHub Actions runs the pure Java geometry tests, static resource validation, and a real Gradle/Fabric build. The source is supplied uncompiled; a successful compile and in-game functionality must be verified via GitHub and Minecraft.

## IMPORTANT: BACK UP YOUR WORLD

The Underworld migrates v3.0–v3.2 chunks by clearing their existing land/ruins and generating the v3.3 layout, **destroying any player builds or chests in affected Underworld chunks.** The vanilla Nether is never changed by this mod. Existing v3.3 chunks are marked and skip regeneration.

## Note about generated portals

Portal blocks are *real* vanilla portal blocks in frames. The server-side Underworld-to-Overworld redirect executes upon contact, prior to vanilla's delayed Nether portal transition. The Overworld destination is the nearest safe air space on a solid surface (up to 24 blocks horizontal displacement); nothing is placed in the Overworld. If no safe site is found, teleportation is refused. The spawning height in the Nether when returning is **always -7**, even if it causes an immediate fall.

## Architectural notes

Terrain and structure locations are pure functions of coordinates + world seed. Generation writes only the current chunk (no recursive neighbor loading), including containers and portal frame pieces. The structure rejection algorithm uses the same candidate neighborhood regardless of chunk load order. Loot chests carry a custom loot-table key and per-location deterministic seed; they populate when first opened.

## Sources / rights

Original procedural geometry, not copied from any YouTube map. Minecraft is a trademark of Mojang/Microsoft. License: MIT, as provided in LICENSE.


## GitHub JSON diagnostic patch (2026-10-12)

The resource validator now reports the **full filename and surrounding lines** for any malformed JSON.
The bundled JSON files were parsed successfully. If your repository still fails on `Extra data: line 397`, replace the repository files with this exact project instead of appending contents into an existing JSON file.

This patch does **not** establish successful Minecraft world loading or complete Gradle compilation. Back up the world and read `logs/latest.log` if the game still asks for Safe Mode.

## GitHub Actions validation repair (2026-10-12)

- `scripts/validate_resources.py` now reads `mod_version` from `gradle.properties` instead of insisting on the literal text `mod_version=3.3.2`. That assertion was producing a false failure when repository version data diverged.
- The GitHub artifact upload now uses `build/libs/*.jar` and excludes `*-sources.jar`, so a version change cannot break the upload path.
- `scripts/verify_artifact.py` runs after **the actual Gradle build** to inspect the compiled JAR, confirm required Java classes/resources are present, verify all embedded JSON parses, and check that the expanded Fabric version matches `gradle.properties`.
- The Java terrain and placement tests and the 26.3 loot checks remain enabled.
- **A successful GitHub Actions build does not prove world loading works.** Start a fresh test world/server and inspect `logs/latest.log` before updating an existing saved world. Do not choose safe mode on the original save. Make a backup first.
- When updating GitHub, **replace the entire repository source** with this ZIP's contents. Do not append JSON files or retain older copies of the Python validator. The main build workflow is `.github/workflows/build.yml`.
