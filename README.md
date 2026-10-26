# CommandDumper

A client-side Fabric mod for Minecraft 26.2. Run `/dumpcommands` while connected
to a world or server to save the available command paths in
`dumpedcommands/<server-address>.txt` inside your Minecraft game directory.
Single-player dumps use `local.txt`, and colons in server addresses become
underscores. Output is sorted alphabetically.

The dump includes literal subcommands and argument placeholders, such as
`pay <player> <amount>`, when they are present in the client's command tree.
Placeholders show argument names rather than possible values. Some server
plugins expose only a generic `<args>` argument; their internal subcommands
cannot be recovered from the tree.

## Requirements

- Minecraft 26.2
- Java 25 or newer
- Fabric Loader 0.19.5 or newer
- Fabric API for Minecraft 26.2 (built against 0.161.0+26.2)
- Fabric Language Kotlin 1.14.2+kotlin.2.4.21 or newer

## Build

Set `JAVA_HOME` to a JDK 25 installation, then run:

```powershell
.\gradlew.bat build
```

On macOS/Linux, use `./gradlew build`. The mod JAR is written to
`build/libs/CommandDumper-1.4.1.jar`. Install it in your Minecraft `mods` folder
alongside Fabric API and Fabric Language Kotlin.

To check the mod in game, run `./gradlew runClient`, open a single-player world
with cheats enabled, and run `/dumpcommands`. Check that `dumpedcommands/local.txt`
contains sorted command paths, then repeat on a multiplayer server to check its
address-based filename and available commands.
