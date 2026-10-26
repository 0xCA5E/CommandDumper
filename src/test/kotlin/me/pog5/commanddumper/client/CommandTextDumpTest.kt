package me.pog5.commanddumper.client

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

class CommandTextDumpTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun initializeMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun eachBranchIsOneCompleteCommandWithoutDuplicateParents() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("autotorchset").then(literal<String>("open")))
        dispatcher.register(literal<String>("axiomconfig"))
        dispatcher.register(literal<String>("pay").then(argument<String, String>("args", StringArgumentType.greedyString())))
        dispatcher.register(literal<String>("server").then(argument<String, String>("server", StringArgumentType.word())))
        assertEquals("/autotorchset open\n/axiomconfig\n/pay <args...>\n/server <server>\n", output(dispatcher))
    }

    @Test
    fun parameterOrderAndIntegerConstraintsArePreserved() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("reward")
            .then(argument<String, String>("player", StringArgumentType.word())
                .then(argument<String, Int>("amount", IntegerArgumentType.integer(1, 100)))))
        assertEquals("/reward <player> <amount: integer 1–100>\n", output(dispatcher))
    }

    @Test
    fun wordQuotedAndRemainingTextHaveReadableLabels() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "word", StringArgumentType.word())
        addArgument(dispatcher, "quoted", StringArgumentType.string())
        addArgument(dispatcher, "rest", StringArgumentType.greedyString())
        assertEquals("/quoted <value: text>\n/rest <value...>\n/word <value>\n", output(dispatcher))
    }

    @Test
    fun defaultNumericBoundsAreNotDumped() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "int", IntegerArgumentType.integer())
        addArgument(dispatcher, "long", LongArgumentType.longArg())
        addArgument(dispatcher, "float", FloatArgumentType.floatArg())
        addArgument(dispatcher, "double", DoubleArgumentType.doubleArg())
        assertEquals("/double <value: number>\n/float <value: number>\n/int <value: integer>\n/long <value: integer>\n", output(dispatcher))
    }

    @Test
    fun numericRangesAndOneSidedLimitsArePreserved() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "minimum", IntegerArgumentType.integer(1))
        addArgument(dispatcher, "maximum", DoubleArgumentType.doubleArg(-Double.MAX_VALUE, 5.0))
        addArgument(dispatcher, "decimal", FloatArgumentType.floatArg(0.5f, 2.5f))
        addArgument(dispatcher, "long", LongArgumentType.longArg(1, 1000))
        assertEquals("/decimal <value: number 0.5–2.5>\n/long <value: integer 1–1000>\n/maximum <value: number ≤5>\n/minimum <value: integer ≥1>\n", output(dispatcher))
    }

    @Test
    fun booleanArgumentsShowTheirActualChoices() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "toggle", BoolArgumentType.bool())
        assertEquals("/toggle <value: true|false>\n", output(dispatcher))
    }

    @Test
    fun minecraftParameterTypesUseHumanNamesAndPreserveMultiplicity() {
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "player", EntityArgument.player())
        addArgument(dispatcher, "players", EntityArgument.players())
        addArgument(dispatcher, "entity", EntityArgument.entity())
        addArgument(dispatcher, "entities", EntityArgument.entities())
        addArgument(dispatcher, "position", Vec3Argument.vec3())
        assertEquals("/entities <value: entities>\n/entity <value: entity>\n/player <value: player>\n/players <value: players>\n/position <value: position>\n", output(dispatcher))
    }

    @Test
    fun aliasesPointToTheirTargetAndRootRedirectsDoNotExpand() {
        val dispatcher = CommandDispatcher<String>()
        val target = dispatcher.register(literal<String>("target").then(literal<String>("child")))
        dispatcher.register(literal<String>("alias").redirect(target))
        dispatcher.register(literal<String>("repeat").fork(dispatcher.root) { listOf(it.source) })
        assertEquals("/alias → /target\n/repeat → /\n/target child\n", output(dispatcher))
    }

    @Test
    fun redirectsToArgumentsUseTheReadableParameterPath() {
        val dispatcher = CommandDispatcher<String>()
        val target = argument<String, Int>("amount", IntegerArgumentType.integer(1, 100))
            .then(literal<String>("confirm")).build()
        dispatcher.register(literal<String>("target").then(target))
        dispatcher.register(literal<String>("alias").redirect(target))
        assertEquals("/alias → /target <amount: integer 1–100>\n/target <amount: integer 1–100> confirm\n", output(dispatcher))
    }

    @Test
    fun detachedRedirectTargetsShowTheirAvailableSyntaxInline() {
        val dispatcher = CommandDispatcher<String>()
        val target = literal<String>("detached").then(literal<String>("child")).build()
        dispatcher.register(literal<String>("alias").redirect(target))
        assertEquals("/alias child\n", output(dispatcher))
    }

    @Test
    fun sharedChildrenAppearUnderBothParents() {
        val dispatcher = CommandDispatcher<String>()
        val shared = literal<String>("shared").build()
        dispatcher.register(literal<String>("a").then(shared))
        dispatcher.register(literal<String>("b").then(shared))
        assertEquals("/a shared\n/b shared\n", output(dispatcher))
    }

    @Test
    fun childCyclesBecomePathReferences() {
        val dispatcher = CommandDispatcher<String>()
        val loop = literal<String>("loop").build()
        loop.addChild(loop)
        dispatcher.root.addChild(loop)
        assertEquals("/loop loop → /loop\n", output(dispatcher))
    }

    @Test
    fun unknownTypesKeepTheParameterWithoutInventingDetails() {
        val unknown = object : ArgumentType<String> {
            override fun parse(reader: StringReader): String = error("Must not parse input")
            override fun getExamples(): Collection<String> = error("Must not generate examples")
        }
        val dispatcher = CommandDispatcher<String>()
        addArgument(dispatcher, "custom", unknown)
        assertEquals("/custom <value>\n", output(dispatcher))
    }

    @Test
    fun exportDoesNotExecuteQuerySuggestionsOrEvaluateRequirements() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("custom")
            .requires { error("Must not evaluate requirements") }
            .executes { error("Must not execute commands") }
            .then(argument<String, String>("args", StringArgumentType.greedyString())
                .suggests { _, _ -> error("Must not query completions") }))
        assertEquals("/custom\n", output(dispatcher))
    }

    @Test
    fun optionalGenericCatchAllsLeaveJustTheCommand() {
        val dispatcher = CommandDispatcher<String>()
        for (name in listOf("args", "Args", "arguments")) {
            dispatcher.register(literal<String>(name)
                .executes { error("Must not execute commands") }
                .then(argument<String, String>(name, StringArgumentType.greedyString())))
        }
        assertEquals("/Args\n/args\n/arguments\n", output(dispatcher))
    }

    @Test
    fun requiredGenericArgumentsAndSpecificOptionalParametersRemainVisible() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("required")
            .then(argument<String, String>("args", StringArgumentType.greedyString())))
        dispatcher.register(literal<String>("message").executes { 1 }
            .then(argument<String, String>("message", StringArgumentType.greedyString())))
        dispatcher.register(literal<String>("typed").executes { 1 }
            .then(argument<String, Int>("args", IntegerArgumentType.integer(1))))
        dispatcher.register(literal<String>("word").executes { 1 }
            .then(argument<String, String>("args", StringArgumentType.word())))
        assertEquals("/message <message...>\n/required <args...>\n/typed <args: integer ≥1>\n/word <args>\n", output(dispatcher))
    }

    @Test
    fun hidingCatchAllsRetainsSubcommandsAndAliasTargets() {
        val dispatcher = CommandDispatcher<String>()
        val args = argument<String, String>("args", StringArgumentType.greedyString()).build()
        dispatcher.register(literal<String>("parent").executes { 1 }
            .then(args)
            .then(literal<String>("child").executes { 1 }
                .then(argument<String, String>("arguments", StringArgumentType.greedyString()))))
        dispatcher.register(literal<String>("alias").redirect(args))
        assertEquals("/alias → /parent\n/parent\n/parent child\n", output(dispatcher))
    }

    @Test
    fun genericArgumentsWithFurtherStructureAreNotHidden() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("structured").executes { 1 }
            .then(argument<String, String>("args", StringArgumentType.greedyString())
                .then(literal<String>("confirm"))))
        assertEquals("/structured <args...> confirm\n", output(dispatcher))
    }

    @Test
    fun commandNamesKeepTheirPunctuationAndUnicode() {
        val dispatcher = CommandDispatcher<String>()
        for (name in listOf("type", "children", "alias", "/pos1", "雪")) dispatcher.register(literal<String>(name))
        assertEquals("//pos1\n/alias\n/children\n/type\n/雪\n", output(dispatcher))
    }

    @Test
    fun branchesWithTheSameLabelDoNotOverwriteEachOther() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("custom")
            .then(literal<String>("<value>").then(literal<String>("first")))
            .then(argument<String, String>("value", StringArgumentType.word()).then(literal<String>("second"))))
        assertEquals("/custom <value> first\n/custom <value> second\n", output(dispatcher))
    }

    @Test
    fun outputIsAlphabeticalAndRepeatable() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("z"))
        dispatcher.register(literal<String>("a").then(literal<String>("z")).then(literal<String>("a")))
        val text = output(dispatcher)
        assertEquals("/a a\n/a z\n/z\n", text)
        assertEquals(text, output(dispatcher))
    }

    @Test
    fun distinctArgumentBranchesRetainTheirParameters() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("team")
            .then(argument<String, String>("first", StringArgumentType.word()).then(literal<String>("confirm")))
            .then(argument<String, String>("second", StringArgumentType.word()).then(literal<String>("confirm"))))
        assertEquals("/team <first> confirm\n/team <second> confirm\n", output(dispatcher))
    }

    @Test
    fun noCommandsProduceAnEmptyFile() {
        assertEquals("", output(CommandDispatcher<String>()))
    }

    @Test
    fun sectionsKeepSameNamedCommandsSeparateAfterTheTreesAreMerged() {
        val server = CommandDispatcher<String>()
        server.register(literal<String>("shared").then(literal<String>("server")))
        server.register(literal<String>("pay").then(argument<String, String>("args", StringArgumentType.greedyString())))
        val snapshot = output(server)

        val client = CommandDispatcher<String>()
        client.register(literal<String>("shared").then(literal<String>("client")))
        client.register(literal<String>("dumpcommands"))
        client.root.children.forEach { server.root.addChild(it) }

        assertEquals(
            "Server commands\n/pay <args...>\n/shared server\n\nClient commands\n/dumpcommands\n/shared client\n",
            CommandTextDump.exportSections(snapshot, client)
        )
    }

    @Test
    fun sectionsKeepAliasesWithTheirOwnCommandTree() {
        val server = CommandDispatcher<String>()
        val serverTarget = server.register(literal<String>("target").then(literal<String>("server")))
        server.register(literal<String>("alias").redirect(serverTarget))
        val client = CommandDispatcher<String>()
        val clientTarget = client.register(literal<String>("target").then(literal<String>("client")))
        client.register(literal<String>("alias").redirect(clientTarget))
        assertEquals(
            "Server commands\n/alias → /target\n/target server\n\nClient commands\n/alias → /target\n/target client\n",
            CommandTextDump.exportSections(output(server), client)
        )
    }

    @Test
    fun emptyTreesStillHaveBothSections() {
        assertEquals("Server commands\n\nClient commands\n", CommandTextDump.exportSections<String>("", null))
        assertEquals("Server commands\n\nClient commands\n",
            CommandTextDump.exportSections("", CommandDispatcher<String>()))
    }

    private fun <T> addArgument(dispatcher: CommandDispatcher<String>, name: String, type: ArgumentType<T>) {
        dispatcher.register(literal<String>(name).then(argument<String, T>("value", type)))
    }

    private fun output(dispatcher: CommandDispatcher<String>): String = CommandTextDump.export(dispatcher)
}
