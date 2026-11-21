# BoatQOL

Brings the modern (1.9+) boat to older Minecraft versions: steer with A/D instead of where you look, boats that don't break on landing or in collisions, a boat item drop, and an optional "use modern ice slide" toggle (off by default, so ice behaves as it did in that version). Works in multiplayer (install on both server and clients).

Targets, in build order:

1. Beta 1.7.3 (Babric) in `b1.7.3/`
2. Better than Adventure 8 (Babric) in `bta/`
3. 1.7.10 (Forge) in `forge-1.7.10/`

See [docs/SPEC.md](docs/SPEC.md) for the exact behavior every port follows.

## Developing

The repository root is one Gradle build with each port as a module. Gradle needs **JDK 21**
(the mods themselves are compiled for Java 8).

In IntelliJ IDEA:

1. **File > Open** the repository root (the folder with `settings.gradle`) and trust the Gradle project.
2. If it asks, set **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM** to a JDK 21.
3. After the Gradle sync finishes, the run configurations list has **Minecraft Client (:b1.7.3)**,
   **Minecraft Server (:b1.7.3)**, **Minecraft Client (:bta)**, **Minecraft Server (:bta)**,
   **Minecraft Client (:forge-1.7.10)** and **Minecraft Server (:forge-1.7.10)**. If the Babric ones
   don't show up, run the `ideaSyncTask` Gradle task once
   (Gradle tool window > BoatQOL > b1.7.3 > ide > ideaSyncTask).

The 1.7.10 Forge port runs Minecraft on Java 8 (and decompiles it with Java 17). Gradle downloads
both automatically the first time. Its dev server asks you to accept the Minecraft EULA in
`forge-1.7.10/run/eula.txt` before it starts.

From a terminal: `./gradlew :b1.7.3:runClient` (or `:bta:runClient`, `:forge-1.7.10:runClient`), `./gradlew :b1.7.3:runServer`, or `./gradlew build`
(jars end up in `<port>/build/libs/`).
