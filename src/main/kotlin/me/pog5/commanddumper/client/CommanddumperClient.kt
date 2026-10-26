package me.pog5.commanddumper.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandBuildContext
import net.minecraft.network.chat.Component.literal
import java.io.File
import java.io.IOException


class CommanddumperClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher: CommandDispatcher<FabricClientCommandSource>, registryAccess: CommandBuildContext ->
            dispatcher.register(
                ClientCommands.literal("dumpcommands").executes { context: CommandContext<FabricClientCommandSource> ->
                    context.source.sendFeedback(literal("Dumping commands...").withStyle(ChatFormatting.GRAY))
                    dumpCommands(context.source)
                    context.source.sendFeedback(literal("Done!").withStyle(ChatFormatting.GREEN))
                    0
                }
            )
        })
    }

    private fun dumpCommands(source: FabricClientCommandSource) {
        val client = Minecraft.getInstance()
        val player = client.player
        val networkHandler = client.connection

        if (networkHandler == null || player == null) {
            source.sendError(literal("Not connected to a server"))
            return
        }

        // Get server address (use "local" if in single-player)
        val serverAddress = client.currentServer?.ip ?: "local"

        // Get the command dispatcher containing all available commands
        val commandDispatcher = networkHandler.commands

        // Collect all commands recursively
        val commands = mutableListOf<String>()
        collectCommands(commandDispatcher.root, "", commands)

        // Sort the commands alphabetically
        commands.sort()

        // Ensure the dumpedcommands directory exists
        val dumpedCommandsDir = File(client.gameDirectory, "dumpedcommands")
        if (!dumpedCommandsDir.exists()) {
            dumpedCommandsDir.mkdirs()
        }

        // Prepare the output file with the server IP as the filename
        val fileName = serverAddress.replace(":", "_") + ".txt"
        val file = File(dumpedCommandsDir, fileName)

        try {
            // Write all commands to the file
            file.writeText(commands.joinToString("\n"))
            source.sendFeedback(literal("Commands dumped to ${file.absolutePath}"))
        } catch (e: IOException) {
            source.sendError(literal("Failed to write commands to file: ${e.message}"))
        }
    }

    private fun collectCommands(
        node: CommandNode<*>,
        prefix: String,
        commands: MutableList<String>
    ) {
        var newPrefix = prefix
        if (node is LiteralCommandNode<*> || node is ArgumentCommandNode<*, *>) {
            newPrefix = if (prefix.isEmpty()) node.usageText else "$prefix ${node.usageText}"
            commands.add(newPrefix)
        }

        // Recursively collect child commands
        node.children.forEach { child ->
            collectCommands(child, newPrefix, commands)
        }
    }
}
