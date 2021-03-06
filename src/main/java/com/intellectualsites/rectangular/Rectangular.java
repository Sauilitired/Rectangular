package com.intellectualsites.rectangular;

import com.google.common.collect.ImmutableSet;
import com.intellectualsites.commands.Command;
import com.intellectualsites.rectangular.command.RectangularCommandManager;
import com.intellectualsites.rectangular.command.impl.*;
import com.intellectualsites.rectangular.database.RectangularDB;
import com.intellectualsites.rectangular.database.RectangularDBMySQL;
import com.intellectualsites.rectangular.manager.*;
import lombok.NonNull;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public final class Rectangular {

    public static Command getCommandManager() {
        return get().commandManager;
    }

    public static EventManager getEventManager() {
        return get().eventManager;
    }

    public static ServiceManager getServiceManager() {
        return get().serviceManager;
    }

    public static WorldManager getWorldManager() {
        return get().worldManager;
    }

    public static MessageManager getMessageManager() {
        return get().messageManager;
    }

    public static RectangularDB getDatabase() {
        return get().database;
    }

    public static RegionManager getRegionManager() {
        return get().regionManager;
    }

    private static Rectangular rectangular;

    public static void setup(@NonNull final ServiceManager provider) throws IllegalAccessException {
        if (rectangular != null) {
            throw new IllegalAccessException("Cannot setup rectangular when already setup, duh");
        }
        rectangular = new Rectangular(provider);
    }

    public static Rectangular get() {
        return rectangular;
    }

    private Command commandManager = new RectangularCommandManager();
    private EventManager eventManager = new EventManager();
    private ServiceManager serviceManager;
    private RegionManager regionManager;
    private WorldManager worldManager;
    private MessageManager messageManager;
    private RectangularDB database;

    private Rectangular(final ServiceManager provider) {
        this.serviceManager = provider;

        // This will be used later
        // TODO Use this shit
        ImmutableSet<String> ignoredCommands = ImmutableSet.of();
        commandManager.setIgnoreList(ignoredCommands);
        // Add all commands, the ignored ones will not be added
        commandManager.createCommand(new Info());
        commandManager.createCommand(new SetMeta());
        commandManager.createCommand(new Wand());
        commandManager.createCommand(new Create());
        commandManager.createCommand(new Expand());
        commandManager.addCommand(new Help());

        // Setup the service manager
        Consumer<String> logger = s -> provider.logger().info(s);
        provider.logger().info("Rectangular initializing, using service manager: " + provider.getClass().getName());
        //

        try {
            this.messageManager = MessageManager.create(provider.getFolder(), logger, "messages");
        } catch (Exception e) {
            e.printStackTrace();
            provider.shutdown("Failed to create the MessageManager");
            return;
        }

        logger.accept("Loading configuration, from: " + provider.getFolder() + File.separator + "core.yml");
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(new File(provider.getFolder(), "core.yml"));
        Configuration defaults = new MemoryConfiguration();
        ConfigurationSection database = defaults.createSection("database");
        database.set("username", "root");
        database.set("password", "password");
        database.set("port", 3306);
        database.set("host", "localhost");
        database.set("prefix", "rect__");
        yamlConfiguration.setDefaults(defaults);
        try {
            yamlConfiguration.save(new File(provider.getFolder(), "core.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.accept("Connecting to the MySQL database");
        database = yamlConfiguration.getConfigurationSection("database");
        RectangularDB db = new RectangularDBMySQL(
                database.getString("database"),
                database.getString("username"),
                database.getString("password"),
                database.getString("host"),
                database.getInt("port"),
                database.getString("prefix")
        );

        if (!db.testConnection()) {
            provider.shutdown("Couldn't connect to MySQL");
            return; // Not even needed, but keeping it there anyhow
        }

        db.createSchema();

        logger.accept("Connection established!");

        this.database = db;
        this.worldManager = provider.getWorldManager();

        ContainerManager containerManager = new ContainerManager();
        containerManager.addContainerFactory(this.worldManager);

        this.regionManager = new RegionManager(containerManager);

        logger.accept("Loading regions async...");
        provider.runAsync(() -> regionManager.load());
    }

}
