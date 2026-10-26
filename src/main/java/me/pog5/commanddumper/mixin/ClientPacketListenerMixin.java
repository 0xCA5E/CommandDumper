package me.pog5.commanddumper.mixin;

import com.mojang.brigadier.CommandDispatcher;
import me.pog5.commanddumper.client.CommandTextDump;
import me.pog5.commanddumper.client.ServerCommandSnapshot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Run before Fabric's default-priority RETURN hook when the field write precedes RETURN.
@Mixin(value = ClientPacketListener.class, priority = 1100)
public abstract class ClientPacketListenerMixin implements ServerCommandSnapshot {
    @Shadow
    private CommandDispatcher<SharedSuggestionProvider> commands;

    @Unique
    private String commanddumper$serverCommands;

    // Snapshot the new server tree before Fabric adds client commands at RETURN.
    @Inject(method = "handleCommands", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;commands:Lcom/mojang/brigadier/CommandDispatcher;",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void commanddumper$captureServerCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
        commanddumper$serverCommands = CommandTextDump.INSTANCE.export(commands);
    }

    @Override
    public String commanddumper$getServerCommands() {
        return commanddumper$serverCommands;
    }
}
