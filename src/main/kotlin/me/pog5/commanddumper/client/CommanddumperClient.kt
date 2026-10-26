package me.pog5.commanddumper.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
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
                    dumpCommands(context.source)
                }
            )
        })
    }

    private fun dumpCommands(source: FabricClientCommandSource): Int {
        val client = Minecraft.getInstance()
        val connection = client.connection
        if (connection == null || client.player == null) {
            source.sendError(literal("Not connected to a server"))
            return 0
        }
        val serverAddress = client.currentServer?.ip ?: "local"
        val file = File(File(client.gameDirectory, "dumpedcommands"),
            serverAddress.replace(":", "_") + ".txt")
        try {
            val text = CommandTextDump.export(connection.commands)
            file.parentFile.mkdirs()
            file.writeText(text)
            source.sendFeedback(literal("Commands exported to ${file.absolutePath}"))
            return 1
        } catch (e: IOException) {
            source.sendError(literal("Failed to write commands: ${e.message}"))
            return 0
        }
    }
}
