package me.pog5.commanddumper.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.tree.CommandNode
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandTextDumpTest {
    @Test
    fun `argument nodes do not create duplicate root command lines`() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("acrobatics").then(argument<String, String>("args", StringArgumentType.greedyString())))
        dispatcher.register(literal<String>("actoggle").then(argument<String, String>("arguments", StringArgumentType.greedyString())))
        dispatcher.register(literal<String>("adminchat").then(argument<String, String>("arguments", StringArgumentType.greedyString())))
        assertEquals(listOf("acrobatics", "actoggle", "adminchat"), dump(dispatcher.root))
    }

    @Test
    fun `literal subcommands are retained and duplicate paths are collapsed`() {
        val dispatcher = CommandDispatcher<String>()
        dispatcher.register(literal<String>("team")
            .then(argument<String, String>("first", StringArgumentType.word()).then(literal<String>("confirm")))
            .then(argument<String, String>("second", StringArgumentType.word()).then(literal<String>("confirm"))))
        dispatcher.register(literal<String>("autotorchset").then(literal<String>("open")))
        assertEquals(listOf("autotorchset", "autotorchset open", "team", "team confirm"), dump(dispatcher.root))
    }

    private fun dump(root: CommandNode<*>): List<String> {
        val result = sortedSetOf<String>()
        val collect = CommanddumperClient::class.java.getDeclaredMethod(
            "collectCommands", CommandNode::class.java, String::class.java, MutableSet::class.java)
        collect.isAccessible = true
        collect.invoke(CommanddumperClient(), root, "", result)
        return result.toList()
    }
}
