package me.pog5.commanddumper.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import net.minecraft.SharedConstants
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.server.Bootstrap
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun approvedFormatContainsOnlyCommandsAndParameters() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("autotorchset").then(literal<String>("open")))
        dispatcher.register(literal<String>("axiomconfig"))
        dispatcher.register(literal<String>("pay").then(argument<String, String>("args", StringArgumentType.greedyString())))
        dispatcher.register(literal<String>("server").then(argument<String, String>("server", StringArgumentType.word())))
        assertEquals(JsonParser.parseString("""
            {
              "autotorchset": {"open": {}},
              "axiomconfig": {},
              "pay": {"<args...>": {}},
              "server": {"<server>": {}}
            }
        """), output(dispatcher))
    }

    @Test
    fun parameterOrderAndIntegerConstraintsStayNested() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("reward")
            .then(argument<String, String>("player", StringArgumentType.word())
                .then(argument<String, Int>("amount", IntegerArgumentType.integer(1, 100)))))
        val tree = output(dispatcher)
        assertEquals(JsonParser.parseString("""
            {"reward": {"<player>": {"<amount: integer 1–100>": {}}}}
        """), tree)
    }

    @Test
    fun wordQuotedAndRemainingTextHaveReadableLabels() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "word", StringArgumentType.word())
        addArgument(dispatcher, "quoted", StringArgumentType.string())
        addArgument(dispatcher, "rest", StringArgumentType.greedyString())
        val tree = output(dispatcher)
        assertTrue(tree.getAsJsonObject("word").has("<value>"))
        assertTrue(tree.getAsJsonObject("quoted").has("<value: text>"))
        assertTrue(tree.getAsJsonObject("rest").has("<value...>"))
    }

    @Test
    fun defaultNumericBoundsAreNotDumped() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "int", IntegerArgumentType.integer())
        addArgument(dispatcher, "long", LongArgumentType.longArg())
        addArgument(dispatcher, "float", FloatArgumentType.floatArg())
        addArgument(dispatcher, "double", DoubleArgumentType.doubleArg())
        val tree = output(dispatcher)
        assertTrue(tree.getAsJsonObject("int").has("<value: integer>"))
        assertTrue(tree.getAsJsonObject("long").has("<value: integer>"))
        assertTrue(tree.getAsJsonObject("float").has("<value: number>"))
        assertTrue(tree.getAsJsonObject("double").has("<value: number>"))
    }

    @Test
    fun numericRangesAndOneSidedLimitsArePreserved() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "minimum", IntegerArgumentType.integer(1))
        addArgument(dispatcher, "maximum", DoubleArgumentType.doubleArg(-Double.MAX_VALUE, 5.0))
        addArgument(dispatcher, "decimal", FloatArgumentType.floatArg(0.5f, 2.5f))
        addArgument(dispatcher, "long", LongArgumentType.longArg(1, 1000))
        val tree = output(dispatcher)
        assertTrue(tree.getAsJsonObject("minimum").has("<value: integer ≥1>"))
        assertTrue(tree.getAsJsonObject("maximum").has("<value: number ≤5>"))
        assertTrue(tree.getAsJsonObject("decimal").has("<value: number 0.5–2.5>"))
        assertTrue(tree.getAsJsonObject("long").has("<value: integer 1–1000>"))
    }

    @Test
    fun booleanArgumentsShowTheirActualChoices() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "toggle", BoolArgumentType.bool())
        assertTrue(output(dispatcher).getAsJsonObject("toggle").has("<value: true|false>"))
    }

    @Test
    fun minecraftParameterTypesUseHumanNamesAndPreserveMultiplicity() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "player", EntityArgument.player())
        addArgument(dispatcher, "players", EntityArgument.players())
        addArgument(dispatcher, "entity", EntityArgument.entity())
        addArgument(dispatcher, "entities", EntityArgument.entities())
        addArgument(dispatcher, "position", Vec3Argument.vec3())
        val tree = output(dispatcher)
        assertTrue(tree.getAsJsonObject("player").has("<value: player>"))
        assertTrue(tree.getAsJsonObject("players").has("<value: players>"))
        assertTrue(tree.getAsJsonObject("entity").has("<value: entity>"))
        assertTrue(tree.getAsJsonObject("entities").has("<value: entities>"))
        assertTrue(tree.getAsJsonObject("position").has("<value: position>"))
    }

    @Test
    fun aliasesArePathStringsAndRootRedirectsDoNotExpand() {
        val dispatcher = CommandDispatcher<String>()
        val target = dispatcher.register(literal<String>("target").then(literal<String>("child")))
        dispatcher.register(literal<String>("alias").redirect(target))
        dispatcher.register(literal<String>("repeat").fork(dispatcher.root) { listOf(it.source) })
        val tree = output(dispatcher)
        assertEquals("/target", tree["alias"].asString)
        assertEquals("/", tree["repeat"].asString)
        assertEquals(JsonParser.parseString("""{"child": {}}"""), tree["target"])
    }

    @Test
    fun redirectsToArgumentsUseTheReadableParameterPath() {
        val dispatcher = CommandDispatcher<String>()
        val target = argument<String, Int>("amount", IntegerArgumentType.integer(1, 100))
            .then(literal<String>("confirm")).build()
        dispatcher.register(literal<String>("target").then(target))
        dispatcher.register(literal<String>("alias").redirect(target))
        assertEquals("/target <amount: integer 1–100>", output(dispatcher)["alias"].asString)
    }

    @Test
    fun detachedRedirectTargetsShowTheirAvailableSyntaxInline() {
        val dispatcher = CommandDispatcher<String>()
        val target = literal<String>("detached").then(literal<String>("child")).build()
        dispatcher.register(literal<String>("alias").redirect(target))
        assertEquals(JsonParser.parseString("""{"alias": {"child": {}}}"""), output(dispatcher))
    }

    @Test
    fun sharedChildrenAppearUnderBothParents() {
        val dispatcher = CommandDispatcher<String>()
        val shared = literal<String>("shared").build()
        dispatcher.register(literal<String>("a").then(shared))
        dispatcher.register(literal<String>("b").then(shared))
        assertEquals(JsonParser.parseString("""{"a": {"shared": {}}, "b": {"shared": {}}}"""), output(dispatcher))
    }

    @Test
    fun childCyclesBecomePathReferences() {
        val dispatcher = CommandDispatcher<String>()
        val loop = literal<String>("loop").build()
        loop.addChild(loop)
        dispatcher.root.addChild(loop)
        assertEquals(JsonParser.parseString("""{"loop": {"loop": "/loop"}}"""), output(dispatcher))
    }

    @Test
    fun unknownTypesKeepTheParameterWithoutInventingDetails() {
        val unknown = object : ArgumentType<String> {
            override fun parse(reader: StringReader): String = error("Must not parse input")
            override fun getExamples(): Collection<String> = error("Must not generate examples")
        }
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "custom", unknown)
        assertEquals(JsonParser.parseString("""{"custom": {"<value>": {}}}"""), output(dispatcher))
    }

    @Test
    fun exportDoesNotExecuteQuerySuggestionsOrEvaluateRequirements() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("custom")
            .requires { error("Must not evaluate requirements") }
            .executes { error("Must not execute commands") }
            .then(argument<String, String>("args", StringArgumentType.greedyString())
                .suggests { _, _ -> error("Must not query completions") }))
        assertEquals(JsonParser.parseString("""{"custom": {"<args...>": {}}}"""), output(dispatcher))
    }

    @Test
    fun literalNamesAreNeverTreatedAsMetadataFields() {
        val dispatcher = CommandDispatcher<String>()
        for (name in listOf("type", "children", "alias", "/pos1")) dispatcher.register(literal<String>(name))
        assertEquals(JsonParser.parseString("""{"type": {}, "children": {}, "alias": {}, "/pos1": {}}"""), output(dispatcher))
    }

    @Test
    fun ambiguousLabelsCannotSilentlyOverwriteABranch() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("custom")
            .then(literal<String>("<value>"))
            .then(argument<String, String>("value", StringArgumentType.word())))
        assertFailsWith<IllegalArgumentException> { output(dispatcher) }
    }

    @Test
    fun outputIsAlphabeticalAndPrettyPrintedWithReadablePlaceholders() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("z"))
        dispatcher.register(literal<String>("a").then(argument<String, String>("args", StringArgumentType.greedyString())))
        val json = CommandMetadataJson.export(dispatcher)
        assertEquals(listOf("a", "z"), JsonParser.parseString(json).asJsonObject.keySet().toList())
        assertTrue(json.contains("\n  \"a\": {\n    \"<args...>\": {}"))
        assertFalse(json.contains("\\u003c"))
        assertTrue(json.endsWith("\n"))
        assertEquals(json, CommandMetadataJson.export(dispatcher))
    }

    @Test
    fun commandNamesRoundTripQuotesNewlinesAndUnicode() {
        val name = "name,\"quoted\"\r\n雪"
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>(name))
        assertTrue(output(dispatcher).has(name))
    }

    @Test
    fun noCommandsProduceAnEmptyObject() {
        assertEquals(JsonObject(), output(CommandDispatcher<String>()))
    }

    private fun <T> addArgument(dispatcher: CommandDispatcher<String>, name: String, type: ArgumentType<T>) {
        dispatcher.register(literal<String>(name).then(argument<String, T>("value", type)))
    }

    private fun output(dispatcher: CommandDispatcher<String>): JsonObject =
        JsonParser.parseString(CommandMetadataJson.export(dispatcher)).asJsonObject
}
