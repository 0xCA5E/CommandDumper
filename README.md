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

## Full metadata JSON

Run `/dumpcommands full` to write pretty-printed UTF-8 JSON to
`dumpedcommands/<server-address>-full.json` (`local-full.json` in single-player).
Neither export executes server commands or requests tab completions.

The document is a tree: the root object contains a `children` array, and every
child contains its own nested `children`. Children are sorted alphabetically.
There are no node IDs or parent/child ID lists. Shared children appear beneath
each parent with the appropriate full command path. `/` represents the root.

Redirects identify their target by command path, so aliases do not recursively
copy their targets. Targets reachable only by redirect have no canonical path;
their metadata is nested under `redirect.target`, with a null path. Cyclic child
links stop at a small object with `cycle: true` and `reference_path` pointing to
the ancestor, rather than repeating forever.

Metadata includes:

- Path, node type, name, usage, and nested children.
- Whether the node is executable, the result of its client-side requirement
  check (`can_use`), redirect target path, and fork flag.
- Argument parser ID, implementation class, and serialized properties such
  as numeric bounds or string mode (`word`, `phrase`, or `greedy`).
- Parser-provided examples (not exhaustive valid values or server suggestions).
- Custom suggestion providers, including their serialization ID and class.
  Unregistered local providers use Minecraft's `minecraft:ask_server` fallback ID.
- Requirement class and serialized permission checks, when available.
- `metadata_errors` for fields a custom node/type cannot expose or serialize;
  the remaining metadata for that node is still exported.

Arguments, redirects, and requirements are grouped into nested objects. Lists
are arrays, flags are booleans, numeric bounds are numbers, and unavailable or
inapplicable values are null. The graph can include commands added by client
mods. Executability, requirement checks, and permission metadata describe the
client's view; they do not establish the server's complete permission rules.
Class names identify client implementations, not server plugin ownership.

Descriptions, help text, permission-plugin nodes, and completion tooltips are
not part of the received command tree and cannot be recovered by this export.
Hidden server commands and plugin-internal syntax remain unavailable.

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
`build/libs/CommandDumper-1.5.2.jar`. Install it in your Minecraft `mods` folder
alongside Fabric API and Fabric Language Kotlin.

To check the mod in game, run `./gradlew runClient`, open a single-player world
with cheats enabled, and run `/dumpcommands`. Check that `dumpedcommands/local.txt`
contains sorted command paths, then repeat on a multiplayer server to check its
address-based filename and available commands.
