# CommandDumper

A client-side Fabric mod for Minecraft 26.2. Run `/dumpcommands` while connected
to a world or server to save the available command paths in
`dumpedcommands/<server-address>.txt` inside your Minecraft game directory.
Single-player dumps use `local.txt`, and colons in server addresses become
underscores. Output is sorted alphabetically, with each literal command path
listed once and no argument placeholders.

The text dump includes literal subcommands, such as `autotorchset open`, when
they are present in the client's command tree. Argument details are available
in the full JSON export. Some server plugins expose only a generic `<args>`
argument; their internal subcommands cannot be recovered from the tree.

## Full command JSON

Run `/dumpcommands full` to write pretty-printed UTF-8 JSON to
`dumpedcommands/<server-address>-full.json` (`local-full.json` in single-player).
Neither export executes server commands or requests tab completions.

Command words and parameters are keys, nested in the order they are entered.
There are no root wrappers, `children` fields, IDs, or implementation metadata.
Branches are sorted alphabetically and `{}` ends a branch:

```json
{
  "autotorchset": {
    "open": {}
  },
  "pay": {
    "<args...>": {}
  },
  "server": {
    "<server>": {}
  }
}
```

Parameter labels carry only useful input information:

- `<name>` names a parameter, such as an ordinary word input.
- `<args...>` consumes the remaining text, including spaces.
- `<message: text>` accepts a word or quoted text.
- `<amount: integer 1–100>` includes an enforced numeric range.
- `<amount: integer ≥1>` or `<amount: number ≤5>` shows a one-sided limit.
- `<enabled: true|false>` shows the boolean choices.
- Minecraft-specific types use readable names, such as `<target: player>` or
  `<location: position>`. Unrecognized types retain their supplied name without
  guessed details. Default machine-size numeric limits are omitted.

Aliases and cycles use command-path strings, for example
`"fcc": "/fabric-command-api-v2:client"`; `/` points to the root. A redirect target
without a reusable path has its available syntax expanded inline. Shared
branches appear under each parent. Labels are never allowed to overwrite one
another silently; an ambiguous export reports an error in chat.

The export reflects the tree available to the client, including client-mod
commands. Some plugins expose only a generic `<args...>` parameter. The mod
does not infer hidden subcommands, descriptions, requiredness, or server
permission rules, and does not generate examples or query suggestions.

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
contains sorted command paths, then repeat on a multiplayer server to check its
address-based filename and available commands.
