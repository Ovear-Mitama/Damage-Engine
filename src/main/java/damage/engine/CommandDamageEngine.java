package damage.engine;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;

/** /damage_engine clear —— 清空当前伤害与评分数据。 */
public class CommandDamageEngine extends CommandBase {
    @Override
    public String getName() {
        return "damage_engine";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/damage_engine clear";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && "clear".equalsIgnoreCase(args[0])) {
            DamageEngineClient.clearAll();
            sender.sendMessage(new TextComponentTranslation("command.damage-engine.cleared"));
            return;
        }
        if (args.length == 1 && "config".equalsIgnoreCase(args[0])) {
            net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new GuiDamageConfig());
            return;
        }
        throw new CommandException("command.damage-engine.usage", getUsage(sender));
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
