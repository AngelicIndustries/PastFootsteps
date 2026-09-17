package org.fentanylsolutions.pastfootsteps.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import org.fentanylsolutions.pastfootsteps.PastFootsteps;
import org.fentanylsolutions.pastfootsteps.config.Config;

/** Local diagnostic commands work on any server without operator permission. */
public final class WalkingCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "pf";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/pf reload|report|status";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        String action = arguments.length == 0 ? "status" : arguments[0];
        if ("reload".equals(action)) {
            FootstepController.INSTANCE.onResourceManagerReload(
                Minecraft.getMinecraft()
                    .getResourceManager());
            sender.addChatMessage(
                new ChatComponentText("PastFootsteps definitions reloaded. /pf report lists diagnostics."));
        } else if ("report".equals(action)) {
            File target = new File(Config.directory, "diagnostics/latest-report.json");
            try {
                Files.createDirectories(
                    target.toPath()
                        .getParent());
                Files.write(
                    target.toPath(),
                    FootstepController.INSTANCE.diagnosticReport()
                        .getBytes(StandardCharsets.UTF_8));
                sender.addChatMessage(new ChatComponentText("PastFootsteps report: " + target.getAbsolutePath()));
            } catch (Exception error) {
                PastFootsteps.LOG.warn("Could not write PF diagnostic report", error);
                sender.addChatMessage(new ChatComponentText("Could not write the report. See the game log."));
            }
        } else sender.addChatMessage(new ChatComponentText(FootstepController.INSTANCE.debugText()));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        return arguments.length == 1 ? getListOfStringsMatchingLastWord(arguments, "reload", "report", "status") : null;
    }
}
