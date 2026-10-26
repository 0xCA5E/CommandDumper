package me.pog5.commanddumper.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import net.minecraft.SharedConstants
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.server.Bootstrap
import net.minecraft.server.permissions.PermissionCheck
import net.minecraft.server.permissions.PermissionProviderCheck
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.server.permissions.PermissionSetSupplier
import net.minecraft.server.permissions.Permissions
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandMetadataJsonTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `exports structured argument metadata with native numbers and booleans`() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("pay")
            .then(argument<String, String>("player", StringArgumentType.word())
                .then(argument<String, Int>("amount", IntegerArgumentType.integer(1, 100))
                    .executes { error("Export must not execute commands") })))
        val rows = nodes(dispatcher, "source")
        val tree = JsonParser.parseString(CommandMetadataJson.export(dispatcher, "source")).asJsonObject
        val pay = tree.getAsJsonArray("children").single().asJsonObject
        val playerNode = pay.getAsJsonArray("children").single().asJsonObject
        val amountNode = playerNode.getAsJsonArray("children").single().asJsonObject
        assertEquals("pay", pay["name"].asString)
        assertEquals("player", playerNode["name"].asString)
        assertEquals("amount", amountNode["name"].asString)
        assertTrue(rows.all { node ->
            listOf("node_id", "parent_ids", "children_ids", "root_id", "nodes").none { node.has(it) }
        })
        val amount = rows.single { it["name"].asString == "amount" }
        assertEquals("/pay <player> <amount>", amount["path"].asString)
        assertEquals("argument", amount["node_type"].asString)
        assertEquals("<amount>", amount["usage"].asString)
        assertTrue(amount["executable"].asJsonPrimitive.isBoolean)
        assertTrue(amount["executable"].asBoolean)
        val arg = amount.getAsJsonObject("argument")
        assertEquals("brigadier:integer", arg["parser_id"].asString)
        val properties = arg.getAsJsonObject("properties")
        assertTrue(properties["min"].asJsonPrimitive.isNumber)
        assertEquals(1, properties["min"].asInt)
        assertEquals(100, properties["max"].asInt)
        assertTrue(amount.getAsJsonArray("examples").size() > 0)
        val player = rows.single { it["name"].asString == "player" }
        assertEquals("word", player.getAsJsonObject("argument").getAsJsonObject("properties")["type"].asString)
        assertTrue(rows.all { it.getAsJsonArray("metadata_errors").isEmpty })
    }

    @Test
    fun `greedy text mode is preserved`() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("say").then(argument<String, String>("message", StringArgumentType.greedyString())))
        val row = nodes(dispatcher, "source").single { it["name"].asString == "message" }
        assertEquals("greedy", row.getAsJsonObject("argument").getAsJsonObject("properties")["type"].asString)
    }

    @Test
    fun `redirects reference nodes without expanding cycles`() {
        val dispatcher = CommandDispatcher<String>()
        val target = dispatcher.register(literal<String>("target").then(literal<String>("child")))
        dispatcher.register(literal<String>("alias").redirect(target))
        dispatcher.register(literal<String>("repeat").fork(dispatcher.root) { listOf(it.source) })
        val rows = nodes(dispatcher, "source")
        assertEquals(5, rows.size)
        val alias = rows.single { it["name"].asString == "alias" }.getAsJsonObject("redirect")
        assertEquals("/target", alias["path"].asString)
        assertFalse(alias.has("target"))
        assertFalse(alias.has("node_id"))
        val repeat = rows.single { it["name"].asString == "repeat" }
        assertEquals("/", repeat.getAsJsonObject("redirect")["path"].asString)
        assertTrue(repeat["fork"].asBoolean)
    }

    @Test
    fun `shared children are nested under each parent`() {
        val dispatcher = CommandDispatcher<String>()
        val shared = literal<String>("shared").build()
        dispatcher.register(literal<String>("a").then(shared))
        dispatcher.register(literal<String>("b").then(shared))
        val rows = nodes(dispatcher, "source")
        assertEquals(5, rows.size)
        val parents = rows.filter { it["name"].asString in setOf("a", "b") }
        assertTrue(parents.all { it.getAsJsonArray("children").single().asJsonObject["name"].asString == "shared" })
        assertEquals(setOf("/a shared", "/b shared"),
            rows.filter { it["name"].asString == "shared" }.map { it["path"].asString }.toSet())
    }

    @Test
    fun `structurally equal nodes are separate identities`() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("a").then(literal<String>("same")))
        dispatcher.register(literal<String>("b").then(literal<String>("same")))
        val rows = nodes(dispatcher, "source").filter { it["name"].asString == "same" }
        assertEquals(2, rows.size)
        assertEquals(setOf("/a same", "/b same"), rows.map { it["path"].asString }.toSet())
    }

    @Test
    fun `child cycles do not recurse forever`() {
        val dispatcher = CommandDispatcher<String>()
        val loop = literal<String>("loop").build()
        loop.addChild(loop)
        dispatcher.root.addChild(loop)
        val rows = nodes(dispatcher, "source")
        assertEquals(3, rows.size)
        val reference = rows.last()
        assertTrue(reference["cycle"].asBoolean)
        assertEquals("/loop", reference["reference_path"].asString)
        assertEquals("loop", reference["name"].asString)
    }

    @Test
    fun `redirect-only targets are nested inline with null paths`() {
        val dispatcher = CommandDispatcher<String>()
        val target = literal<String>("detached").then(literal<String>("child")).build()
        dispatcher.register(literal<String>("alias").redirect(target))
        val rows = nodes(dispatcher, "source")
        assertEquals(4, rows.size)
        assertTrue(rows.single { it["name"].asString == "detached" }["path"].isJsonNull)
        val redirect = rows.single { it["name"].asString == "alias" }.getAsJsonObject("redirect")
        assertTrue(redirect["path"].isJsonNull)
        assertEquals("detached", redirect.getAsJsonObject("target")["name"].asString)
        assertEquals("child", redirect.getAsJsonObject("target").getAsJsonArray("children").single().asJsonObject["name"].asString)
    }

    @Test
    fun `custom suggestion providers are identified but never called`() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("custom").then(argument<String, String>("value", StringArgumentType.word())
            .suggests { _, _ -> error("Export must not query tab completion") }))
        val row = nodes(dispatcher, "source").single { it["name"].asString == "value" }
        val provider = row.getAsJsonObject("argument").getAsJsonObject("suggestion_provider")
        assertEquals("minecraft:ask_server", provider["id"].asString)
        assertTrue(provider["class"].asString.isNotEmpty())
    }

    @Test
    fun `registered provider identifiers are retained`() {
        val dispatcher = CommandDispatcher<SharedSuggestionProvider>()
        dispatcher.register(literal<SharedSuggestionProvider>("custom")
            .then(argument<SharedSuggestionProvider, String>("value", StringArgumentType.word())
                .suggests(SuggestionProviders.AVAILABLE_SOUNDS)))
        val source = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(SharedSuggestionProvider::class.java)) { _, _, _ -> error("Unexpected source access") }
            as SharedSuggestionProvider
        val row = nodes(dispatcher, source).single { it["name"].asString == "value" }
        assertEquals("minecraft:available_sounds",
            row.getAsJsonObject("argument").getAsJsonObject("suggestion_provider")["id"].asString)
    }

    @Test
    fun `unknown argument serializers report an error without dropping the node`() {
        val type = object : ArgumentType<String> {
            override fun parse(reader: StringReader): String = reader.readString()
        }
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("custom").then(argument<String, String>("value", type)))
        val row = nodes(dispatcher, "source").single { it["name"].asString == "value" }
        assertTrue(row.getAsJsonObject("argument")["properties"].isJsonNull)
        assertTrue(row.getAsJsonArray("metadata_errors").any { it.asString.contains("argument_properties") })
        assertEquals(type.javaClass.name, row.getAsJsonObject("argument")["type_class"].asString)
    }

    @Test
    fun `client requirement results and permission checks remain structured`() {
        val dispatcher = CommandDispatcher<PermissionSetSupplier>()
        dispatcher.register(literal<PermissionSetSupplier>("restricted")
            .requires(PermissionProviderCheck(PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER))))
        val source = PermissionSetSupplier { PermissionSet.NO_PERMISSIONS }
        val row = nodes(dispatcher, source).single { it["name"].asString == "restricted" }
        assertFalse(row["can_use"].asBoolean)
        assertTrue(row.getAsJsonObject("requirement")["permissions"].isJsonObject)
        assertTrue(row.getAsJsonArray("metadata_errors").isEmpty)
    }

    @Test
    fun `JSON is indented and preserves quotes newlines Unicode and readable placeholders`() {
        val name = "name,\"quoted\"\r\n雪"
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>(name).then(argument<String, String>("message", StringArgumentType.greedyString())))
        val json = CommandMetadataJson.export(dispatcher, "source")
        val document = JsonParser.parseString(json).asJsonObject
        assertTrue(json.contains("\n  \"path\": \"/\","))
        assertTrue(json.contains("\n  \"children\": ["))
        assertTrue(json.contains("<message>"))
        assertFalse(json.contains("\\u003c"))
        assertTrue(json.endsWith("\n"))
        val row = document.getAsJsonArray("children").single().asJsonObject
        assertEquals(name, row["name"].asString)
        assertEquals("/$name", row["path"].asString)
    }

    @Test
    fun `export is deterministic and null marks inapplicable metadata`() {
        val dispatcher = CommandDispatcher<String>()
        assertEquals(CommandMetadataJson.export(dispatcher, "source"), CommandMetadataJson.export(dispatcher, "source"))
        val root = nodes(dispatcher, "source").single()
        assertEquals("/", root["path"].asString)
        assertEquals("root", root["node_type"].asString)
        assertTrue(root.getAsJsonArray("children").isEmpty)
        assertTrue(root["argument"].isJsonNull)
        assertTrue(root["redirect"].isJsonNull)
    }

    private fun <S> nodes(dispatcher: CommandDispatcher<S>, source: S): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        fun visit(node: JsonObject) {
            result.add(node)
            node.getAsJsonArray("children")?.forEach { visit(it.asJsonObject) }
            val redirect = node["redirect"]
            if (redirect?.isJsonObject == true && redirect.asJsonObject.has("target")) {
                visit(redirect.asJsonObject.getAsJsonObject("target"))
            }
        }
        visit(JsonParser.parseString(CommandMetadataJson.export(dispatcher, source)).asJsonObject)
        return result
    }
}
