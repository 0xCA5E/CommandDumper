package me.pog5.commanddumper.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import com.mojang.serialization.JsonOps
import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.commands.synchronization.ArgumentTypeInfos
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.PermissionProviderCheck
import java.util.IdentityHashMap

internal object CommandMetadataJson {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create()

    fun <S> export(dispatcher: CommandDispatcher<S>, source: S): String {
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
                paths[child] = if (prefix == "/") "/${child.usageText}" else "$prefix ${child.usageText}"
                queue.addLast(child)
            }
        }

        val active = IdentityHashMap<CommandNode<S>, String?>()
        fun serialize(node: CommandNode<S>, path: String?): JsonObject {
            if (active.containsKey(node)) {
                return JsonObject().apply {
                    addProperty("name", node.name)
                    addProperty("reference_path", active[node])
                    addProperty("cycle", true)
                }
            }
            active[node] = path
            val errors = JsonArray()
            fun inspect(field: String, value: () -> JsonElement): JsonElement = try {
                value()
            } catch (e: Exception) {
                errors.add("$field: ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
                JsonNull.INSTANCE
            }
            val argument = node as? ArgumentCommandNode<S, *>
            var parser: String? = null
            val properties = if (argument == null) JsonNull.INSTANCE else inspect("argument_properties") {
                val info = ArgumentTypeInfos.byClass(argument.type)
                parser = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(info)?.toString()
                argumentProperties(info, argument.type)
            }
            val provider = argument?.customSuggestions
            val requirement = node.requirement
            val permissions = if (requirement !is PermissionProviderCheck<*>) JsonNull.INSTANCE else inspect("permissions") {
                PermissionCheck.CODEC.encodeStart(JsonOps.INSTANCE, requirement.test()).getOrThrow()
            }
            val record = JsonObject().apply {
                addProperty("path", path)
                addProperty("node_type", when (node) {
                    is RootCommandNode<*> -> "root"
                    is LiteralCommandNode<*> -> "literal"
                    is ArgumentCommandNode<*, *> -> "argument"
                    else -> "unknown"
                })
                addProperty("name", node.name)
                addProperty("usage", node.usageText)
                addProperty("executable", node.command != null)
                add("can_use", inspect("can_use") { JsonPrimitive(node.canUse(source)) })
                add("redirect", node.redirect?.let { target ->
                    JsonObject().apply {
                        addProperty("path", paths[target])
                        // A detached target has no canonical path, so keep its metadata inline.
                        if (!paths.containsKey(target)) add("target", serialize(target, null))
                    }
                } ?: JsonNull.INSTANCE)
                addProperty("fork", node.isFork)
                add("argument", if (argument == null) JsonNull.INSTANCE else JsonObject().apply {
                    addProperty("parser_id", parser)
                    addProperty("type_class", argument.type.javaClass.name)
                    add("properties", properties)
                    add("suggestion_provider", if (provider == null) JsonNull.INSTANCE else JsonObject().apply {
                        add("id", inspect("suggestion_provider") { JsonPrimitive(SuggestionProviders.getName(provider).toString()) })
                        addProperty("class", provider.javaClass.name.substringBefore('/'))
                    })
                })
                add("examples", inspect("examples") { strings(node.examples.sorted()) })
                add("requirement", JsonObject().apply {
                    addProperty("class", requirement.javaClass.name.substringBefore('/'))
                    add("permissions", permissions)
                })
                add("metadata_errors", errors)
                add("children", JsonArray().also { children ->
                    for (child in node.children.sortedBy { it.name }) {
                        val childPath = when (path) {
                            null -> null
                            "/" -> "/${child.usageText}"
                            else -> "$path ${child.usageText}"
                        }
                        children.add(serialize(child, childPath))
                    }
                })
            }
            active.remove(node)
            return record
        }
        return gson.toJson(serialize(dispatcher.root, "/")) + "\n"
    }

    private fun <A : ArgumentType<*>, T : ArgumentTypeInfo.Template<A>> argumentProperties(
        info: ArgumentTypeInfo<A, T>, argument: A
    ): JsonObject = JsonObject().also { info.serializeToJson(info.unpack(argument), it) }

    private fun strings(values: Collection<String>): JsonArray = JsonArray().also { array ->
        values.forEach { array.add(it) }
    }

}
