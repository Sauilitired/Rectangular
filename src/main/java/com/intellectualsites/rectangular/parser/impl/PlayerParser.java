package com.intellectualsites.rectangular.parser.impl;

import com.intellectualsites.rectangular.Rectangular;
import com.intellectualsites.rectangular.parser.Parser;
import com.intellectualsites.rectangular.parser.ParserResult;
import com.intellectualsites.rectangular.player.RectangularPlayer;

import java.util.UUID;

public class PlayerParser extends Parser<RectangularPlayer> {

    public PlayerParser() {
        super("player", null);
    }

    @Override
    public ParserResult<RectangularPlayer> parse(String in) {
        RectangularPlayer player = null;
        if (in.length() > 16) {
            player = Rectangular.get().getServiceManager().getPlayerManager().getPlayer(UUID.fromString(in));
        } else {
            player = Rectangular.get().getServiceManager().getPlayerManager().getPlayer(in);
        }
        if (player == null) {
            return new ParserResult<>(in + " is not a valid player (might not be online?");
        }
        return new ParserResult<>(player);
    }
}
