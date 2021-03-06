package com.intellectualsites.rectangular.bukkit;

import com.google.common.collect.ImmutableCollection;
import com.intellectualsites.commands.Command;
import com.intellectualsites.commands.CommandManager;
import com.intellectualsites.rectangular.Rectangular;
import com.intellectualsites.rectangular.api.objects.Region;
import com.intellectualsites.rectangular.core.WorldContainer;
import com.intellectualsites.rectangular.item.Item;
import com.intellectualsites.rectangular.parser.Parserable;
import com.intellectualsites.rectangular.player.PlayerEventObserver;
import com.intellectualsites.rectangular.player.PlayerMeta;
import com.intellectualsites.rectangular.player.RectangularPlayer;
import com.intellectualsites.rectangular.vector.Vector2;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BukkitPlayer implements RectangularPlayer {

    private static int idPool = Integer.MIN_VALUE;

    @Getter private final int id;
    @Getter private final Player player;
    @Getter private final PlayerEventObserver eventObserver;

    private PlayerMeta meta;
    private Region topLevelRegion;
    private boolean regionFetched = false;

    BukkitPlayer(Player player) {
        this.id = idPool++;
        this.player = player;
        this.eventObserver = new PlayerEventObserver(this);
        // Make sure that it's loaded properly
        PlayerMeta temp = Rectangular.getServiceManager()
                .getPlayerManager().unloadMeta(player.getUniqueId());
        if (temp == null) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    PlayerMeta temp = Rectangular.getServiceManager()
                            .getPlayerManager().unloadMeta(player.getUniqueId());
                    if (temp == null) {
                        Rectangular.getServiceManager().runSyncDelayed(this, 5L /* 1/4 of a second */);
                    } else {
                        meta = temp;
                    }
                }
            };
            Rectangular.getServiceManager().runSync(runnable);
        } else {
            meta = temp;
        }
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public boolean isOp() {
        return player.isOp();
    }

    @Override
    public boolean hasPermission(String permissionNode) {
        return player.hasPermission(permissionNode);
    }

    @Override
    public boolean isInRegion() {
        return getRegion() != null;
    }

    @Override
    public Region getRegion() {
        if (!regionFetched) {
            regionFetched = true;
            resetRegionCache();
        }
        return topLevelRegion;
    }

    @Override
    public void resetRegionCache() {
        Region old = topLevelRegion;
        topLevelRegion = Rectangular.getRegionManager().
                getHighestLevelRegion(getWorld(),
                        BukkitUtil.locationToVector(player.getLocation()));
        if (topLevelRegion != null && topLevelRegion != old) {
            getEventObserver().onPlayerEnterRegion();
        }
    }

    @Override
    public String getWorld() {
        return player.getWorld().getName();
    }

    @Override
    public WorldContainer getWorldObject() {
        return Rectangular.getWorldManager().getContainer(getWorld());
    }

    @Override
    public void sendMessage(String msg, Object ... arguments) {
        if (msg.equals("null")) {
            return;
        }
        String transformed = ChatColor.translateAlternateColorCodes('&', MessageFormat.format(msg, arguments));
        if (transformed.contains("\n")) {
            player.sendMessage(transformed.split("\n"));
        } else {
            player.sendMessage(transformed);
        }
    }

    private Map<String, Integer> armorStandCache = new HashMap<>();

    @Override
    public void deleteIndicator(double x, double y, double z) {
        String key = x + ";" + y + ";" + z;
        if (!armorStandCache.containsKey(key)) {
            return;
        }
        RectangularPlugin.getNmsImplementation().getArmorStandManager().despawn(this, armorStandCache.get(key));
        armorStandCache.remove(key);
    }

    @Override
    public void deleteIndicators() {
        int[] ids = new int[armorStandCache.size()];
        final int[] index = {0};
        armorStandCache.values().forEach(i -> ids[index[0]++] = i);
        armorStandCache.clear();
        RectangularPlugin.getNmsImplementation().getArmorStandManager().despawn(this, ids);
    }

    @Override
    public void giveItem(Item item) {
        org.bukkit.inventory.ItemStack itemStack = BukkitUtil.itemToItemStack(item);
        player.getInventory().addItem(itemStack);
        player.updateInventory();
    }

    @Override
    public PlayerMeta getMeta() {
        return meta;
    }

    @Override
    public Vector2 getLocation() {
        return BukkitUtil.locationToVector(player.getLocation());
    }

    public static final int INDICATOR_MAX_CHUNKS = 5;
    public static final double INDICATOR_MAX_DISTANCE = NumberConversions.square(
            (double) /* Number of chunks */ INDICATOR_MAX_CHUNKS * 16
    );

    @Override
    public void showIndicator(double x, double y, double z, String colour) {
        // TODO: Make this configurable
        if (player.getLocation().toVector().distanceSquared(new Vector(x, y, z)) > INDICATOR_MAX_DISTANCE) {
            return;
        }
        if (armorStandCache.containsKey( x + ";" + y + ";" + z)) {
            return; // Otherwise it will create buggy duplicates :/
        }
        armorStandCache.put(x + ";" + y + ";" + z, RectangularPlugin.getNmsImplementation().getArmorStandManager().spawn(this, x, y, z, DyeColor.valueOf(colour.toUpperCase())));
    }

    @Override
    public boolean hasAttachment(String a) {
        return player.hasPermission(a);
    }

    @Override
    public void sendRequiredArgumentsList(CommandManager manager, Command cmd, ImmutableCollection<Parserable> required, String usage) {
        sendMessage("&cYou are missing the following arguments (in order)");
        for (Parserable parserable : required) {
            sendMessage("&e &6" + parserable.getName() + " &e| &6" + parserable.getParser().getName() + "&e, Example: &6" + parserable.getParser().getExample());
            sendMessage("&e &6 " + parserable.getDesc());
        }
    }
}
