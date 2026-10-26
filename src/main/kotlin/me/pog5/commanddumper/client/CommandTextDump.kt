package me.pog5.commanddumper.client

import com.google.gson.JsonObject
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.commands.synchronization.ArgumentTypeInfos
import net.minecraft.core.registries.BuiltInRegistries
import java.util.IdentityHashMap

internal object CommandTextDump {
    fun <S> exportSections(serverCommands: String, clientCommands: CommandDispatcher<S>?): String =
        "Server commands\n" + serverCommands + "\nClient commands\n" +
            (clientCommands?.let { export(it) } ?: "")

    fun <S> export(dispatcher: CommandDispatcher<S>): String {
        val labels = IdentityHashMap<CommandNode<S>, String>()
        fun label(node: CommandNode<S>): String = labels.getOrPut(node) {
            if (node is ArgumentCommandNode<*, *>) parameterLabel(node) else node.name
        }
        fun childPath(prefix: String, parent: CommandNode<S>, child: CommandNode<S>): String =
            if (isOptionalCatchAll(parent, child)) prefix
            else if (prefix == "/") "/${label(child)}" else "$prefix ${label(child)}"

        // Redirects use canonical paths instead of recursively expanding aliases.
        val paths = IdentityHashMap<CommandNode<S>, String>()
        paths[dispatcher.root] = "/"
        val queue = ArrayDeque<CommandNode<S>>()
        queue.add(dispatcher.root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val prefix = paths.getValue(node)
            for (child in node.children.sortedBy { it.name }) {
                if (paths.containsKey(child)) continue
                paths[child] = childPath(prefix, node, child)
                queue.addLast(child)
            }
        }

        val lines = sortedSetOf<String>()
        val active = IdentityHashMap<CommandNode<S>, String>()
        fun collect(node: CommandNode<S>, path: String) {
            active[node]?.let {
                lines.add("$path → $it")
                return
            }
            val target = node.redirect
            target?.let { paths[it] }?.let {
                lines.add("$path → $it")
                return
            }
            active[node] = path
            // Detached redirect targets have no reusable path; show their syntax inline.
            if (target != null) {
                collect(target, path)
            } else if (node.children.isEmpty()) {
                if (node !== dispatcher.root) lines.add(path)
            } else {
                for (child in node.children) collect(child, childPath(path, node, child))
            }
            active.remove(node)
        }
        collect(dispatcher.root, "/")
        return lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n")
    }

    private fun isOptionalCatchAll(parent: CommandNode<*>, child: CommandNode<*>): Boolean {
        if (parent.command == null || child !is ArgumentCommandNode<*, *> ||
            child.children.isNotEmpty() || child.redirect != null) return false
        val type = child.type as? StringArgumentType ?: return false
        return type.type == StringArgumentType.StringType.GREEDY_PHRASE &&
            (child.name.equals("args", ignoreCase = true) || child.name.equals("arguments", ignoreCase = true))
    }

    private fun parameterLabel(node: ArgumentCommandNode<*, *>): String {
        val type = node.type
        if (type is StringArgumentType) {
            return when (type.type) {
                StringArgumentType.StringType.SINGLE_WORD -> "<${node.name}>"
                StringArgumentType.StringType.GREEDY_PHRASE -> "<${node.name}...>"
                else -> "<${node.name}: text>"
            }
        }
        val hint = when (type) {
            is BoolArgumentType -> "true|false"
            is IntegerArgumentType -> numberHint("integer", type.minimum, type.maximum, Int.MIN_VALUE, Int.MAX_VALUE)
            is LongArgumentType -> numberHint("integer", type.minimum, type.maximum, Long.MIN_VALUE, Long.MAX_VALUE)
            is FloatArgumentType -> numberHint("number", type.minimum, type.maximum, -Float.MAX_VALUE, Float.MAX_VALUE)
            is DoubleArgumentType -> numberHint("number", type.minimum, type.maximum, -Double.MAX_VALUE, Double.MAX_VALUE)
            else -> minecraftTypeHint(type)
        }
        return if (hint == null) "<${node.name}>" else "<${node.name}: $hint>"
    }

    private fun numberHint(kind: String, min: Number, max: Number, defaultMin: Number, defaultMax: Number): String {
        val lower = if (min == defaultMin) null else min.toString().removeSuffix(".0")
        val upper = if (max == defaultMax) null else max.toString().removeSuffix(".0")
        return when {
            lower != null && upper != null -> "$kind $lower–$upper"
            lower != null -> "$kind ≥$lower"
            upper != null -> "$kind ≤$upper"
            else -> kind
        }
    }

    private fun minecraftTypeHint(type: ArgumentType<*>): String? = try {
        val info = ArgumentTypeInfos.byClass(type)
        val id = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(info)?.path
        val properties = argumentProperties(info, type)
        when (id) {
            "entity" -> {
                val players = properties["type"]?.asString == "players"
                val multiple = properties["amount"]?.asString == "multiple"
                if (players) { if (multiple) "players" else "player" }
                else { if (multiple) "entities" else "entity" }
            }
            "vec2" -> "2D position"
            "vec3" -> "position"
            "block_pos" -> "block position"
            "column_pos" -> "column position"
            "item_stack" -> "item"
            "item_predicate" -> "item filter"
            "resource_location" -> "resource ID"
            "resource", "resource_key", "resource_or_tag", "resource_or_tag_key" -> {
                val registry = properties["registry"]?.asString?.substringAfter(':')?.replace('_', ' ') ?: "resource"
                if (id.contains("or_tag")) "$registry or tag" else registry
            }
            "time" -> "time" + (properties["min"]?.let { " ≥${it.asInt}" } ?: "")
            else -> id?.replace('_', ' ')
        }
    } catch (_: RuntimeException) {
        // Keep an unknown/custom parameter without inventing its type or constraints.
        null
    }

    private fun <A : ArgumentType<*>, T : ArgumentTypeInfo.Template<A>> argumentProperties(
        info: ArgumentTypeInfo<A, T>, argument: A
    ): JsonObject = JsonObject().also { info.serializeToJson(info.unpack(argument), it) }

}
