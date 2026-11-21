package industries.angelic.pastfootsteps.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import industries.angelic.pastfootsteps.PastFootsteps;
import industries.angelic.pastfootsteps.config.Config;

/** Local diagnostic commands work on any server without operator permission. */
public final class WalkingCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "pf";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/pf reload|report|status|acoustics|mappings [all|unmapped] [map]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        String action = arguments.length == 0 ? "status" : arguments[0];
        if ("reload".equals(action)) {
            if (arguments.length != 1) {
                usage(sender);
                return;
            }
            FootstepController.INSTANCE.onResourceManagerReload(
                Minecraft.getMinecraft()
                    .getResourceManager());
            sender.addChatMessage(
                new ChatComponentText("PastFootsteps definitions reloaded. /pf report lists diagnostics."));
        } else if ("report".equals(action)) {
            if (arguments.length != 1) {
                usage(sender);
                return;
            }
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
        } else if ("acoustics".equals(action)) {
            if (arguments.length != 1) {
                usage(sender);
                return;
            }
            File target = new File(Config.directory, "diagnostics/acoustics-export.zip");
            try {
                int count = FootstepController.INSTANCE.exportAcoustics(target);
                sender.addChatMessage(
                    new ChatComponentText("Exported " + count + " acoustic definitions: " + target.getAbsolutePath()));
            } catch (Exception error) {
                PastFootsteps.LOG.warn("Could not export PF acoustics", error);
                sender.addChatMessage(new ChatComponentText("Could not export acoustics. See the game log."));
            }
        } else if ("mappings".equals(action)) {
            writeMappings(sender, arguments);
        } else if ("status".equals(action) && arguments.length <= 1) {
            sender.addChatMessage(new ChatComponentText(FootstepController.INSTANCE.debugText()));
        } else usage(sender);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            return getListOfStringsMatchingLastWord(arguments, "reload", "report", "status", "acoustics", "mappings");
        }
        if (arguments.length == 2 && "mappings".equals(arguments[0])) {
            return getListOfStringsMatchingLastWord(arguments, "all", "unmapped");
        }
        if (arguments.length == 3 && "mappings".equals(arguments[0])) {
            return getListOfStringsMatchingLastWord(
                arguments,
                new ArrayList<String>(FootstepController.INSTANCE.materialMapNames()).toArray(new String[0]));
        }
        return null;
    }

    private static void writeMappings(ICommandSender sender, String[] arguments) {
        if (arguments.length < 1 || arguments.length > 3) {
            usage(sender);
            return;
        }

        String mode = arguments.length >= 2 ? arguments[1] : "all";
        if (!"all".equals(mode) && !"unmapped".equals(mode)) {
            usage(sender);
            return;
        }

        boolean namedMap = arguments.length == 3;
        String map = namedMap ? arguments[2] : "";
        if (namedMap) {
            Set<String> maps = FootstepController.INSTANCE.materialMapNames();
            if (!maps.contains(map)) {
                sender.addChatMessage(new ChatComponentText("Unknown material map: " + map));
                return;
            }
        }

        try {
            String json = FootstepController.INSTANCE.mappingReport("unmapped".equals(mode), map);
            String fileMap = namedMap ? "map-" + map : "shared";
            File target = new File(Config.directory, "diagnostics/mappings-" + mode + "-" + fileMap + ".json");
            Files.createDirectories(
                target.toPath()
                    .getParent());
            Files.write(target.toPath(), json.getBytes(StandardCharsets.UTF_8));
            sender.addChatMessage(new ChatComponentText("PastFootsteps mappings: " + target.getAbsolutePath()));
        } catch (IllegalArgumentException error) {
            sender.addChatMessage(
                new ChatComponentText(error.getMessage() == null ? "Invalid material map." : error.getMessage()));
        } catch (Exception error) {
            PastFootsteps.LOG.warn("Could not write PF mapping diagnostics", error);
            sender.addChatMessage(new ChatComponentText("Could not write mapping diagnostics. See the game log."));
        }
    }

    private static void usage(ICommandSender sender) {
        sender.addChatMessage(
            new ChatComponentText("Usage: /pf reload|report|status|acoustics|mappings [all|unmapped] [map]"));
    }
}
