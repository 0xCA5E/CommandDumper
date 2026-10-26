# CommandDumper

A client-side Fabric mod for Minecraft 26.2. Run `/dumpcommands` while connected
to a world or server to save a readable command reference to
`dumpedcommands/<server-address>.txt` inside your Minecraft game directory.
Single-player dumps use `local.txt`, and colons in server addresses become
underscores. Each run replaces that server's TXT file. There is no `full`
subcommand or JSON export.

The file has two sections: **Server commands**, received from the server, and
**Client commands**, registered locally through Fabric's client command API.
Each section is alphabetized. A name registered on both sides appears in both
sections with its own syntax. In single-player, the integrated server's
commands belong in the server section.

Each complete branch appears once per section, with its parameters in the
order you enter them. Parent paths do not get extra lines simply for containing
children. An illustrative example:

```text
Server commands
/pay <args...>
/server <server>

Client commands
/autotorchset open
/axiomconfig
/dumpcommands
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

Generic trailing `<args...>` or `<arguments...>` placeholders are hidden when
the tree also advertises an execution handler without those arguments. This
keeps catch-all plugin registrations from cluttering the reference. Required
arguments, specifically named parameters such as `<message...>`, typed inputs,
and branches with further structure remain visible. Hiding a placeholder does
not prove that the server ignores arguments; their use is unconfirmed.

An arrow points to an alias or loop's target; `/` means the command root.
Redirect targets without a reusable path have their syntax expanded inline.
Shared branches appear under each parent. Commands whose names start with a
slash retain it, so WorldEdit's `/pos1` appears as `//pos1`.

The server section is captured before Fabric merges in client commands and
refreshes whenever the server sends a new command tree. Reconnecting starts
with a fresh snapshot. The export does not identify which mod or plugin owns a
command; server ownership metadata is not transmitted.

The UTF-8 export reflects the trees available to the client. Some plugins
expose only a generic `<args...>` parameter; their
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
contains both sections with sorted command syntax and parameters, and that
`/dumpcommands` appears under Client commands. Repeat on a multiplayer server
to check its address-based filename and available commands.
