# CommandDumper

A client-side Fabric mod for Minecraft 26.2. Run `/dumpcommands` while connected
to a world or server to save a readable command reference to
`dumpedcommands/<server-address>.txt` inside your Minecraft game directory.
Single-player dumps use `local.txt`, and colons in server addresses become
underscores. Each run replaces that server's TXT file. There is no `full`
subcommand or JSON export.

Each complete branch appears once, in alphabetical order, with its parameters
in the order you enter them. Parent paths do not get extra lines simply for
containing children. For example:

```text
/autotorchset open
/axiomconfig
/fcc → /fabric-command-api-v2:client
/pay <args...>
/server <server>
```

Parameter labels carry useful input information:

- `<name>` names a parameter, such as an ordinary word input.
- `<args...>` consumes the remaining text, including spaces.
- `<message: text>` accepts a word or quoted text.
- `<amount: integer 1–100>` includes an enforced numeric range.
- `<amount: integer ≥1>` or `<amount: number ≤5>` shows a one-sided limit.
- `<enabled: true|false>` shows the boolean choices.
- Minecraft-specific types use readable names, such as `<target: player>` or
  `<location: position>`. Unrecognized types retain their supplied name without
  guessed details. Default machine-size numeric limits are omitted.

An arrow points to an alias or loop's target; `/` means the command root.
Redirect targets without a reusable path have their syntax expanded inline.
Shared branches appear under each parent. Commands whose names start with a
slash retain it, so WorldEdit's `/pos1` appears as `//pos1`.

The UTF-8 export reflects the tree available to the client, including client-mod
commands. Some plugins expose only a generic `<args...>` parameter; their
internal subcommands cannot be recovered from the tree. The mod does not infer
hidden subcommands, descriptions, requiredness, or server permission rules.
It does not execute commands, request tab completions, or generate examples.

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
`build/libs/CommandDumper-1.6.jar`. Install it in your Minecraft `mods` folder
alongside Fabric API and Fabric Language Kotlin.

To check the mod in game, run `./gradlew runClient`, open a single-player world
with cheats enabled, and run `/dumpcommands`. Check that `dumpedcommands/local.txt`
contains sorted command syntax and parameters, then repeat on a multiplayer
server to check its address-based filename and available commands.
