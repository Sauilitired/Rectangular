package com.intellectualsites.rectangular.command.impl;

import com.intellectualsites.commands.Command;
import com.intellectualsites.commands.CommandDeclaration;
import com.intellectualsites.commands.CommandInstance;
import com.intellectualsites.rectangular.api.objects.Region;
import com.intellectualsites.rectangular.config.Message;
import com.intellectualsites.rectangular.core.Rectangle;
import com.intellectualsites.rectangular.player.RectangularPlayer;

@CommandDeclaration(
        command = "info",
        aliases = { "i", "about" }
)
public class Info extends Command {

    @Override
    public boolean onCommand(CommandInstance instance) {
        RectangularPlayer player = (RectangularPlayer) instance.getCaller();
        if (!player.isInRegion()) {
            player.sendMessage("@error.not_in_region");
        } else {
            Region region = player.getRegion();
            if (instance.getArguments().length > 0) {
                if (instance.getArguments()[0].equalsIgnoreCase("rectangle")) {
                    return Message.INFO_RECTANGLE.send(player, region.getRectangle(player.getLocation()));
                }
            }
            player.sendMessage("You're in region: " + region.getId());
            for (Rectangle r : region.getRectangles()) {
                player.sendMessage("R: " + r);
            }
        }
        return true;
    }

}
