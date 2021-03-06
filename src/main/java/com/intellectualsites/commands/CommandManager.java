package com.intellectualsites.commands;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellectualsites.commands.callers.CommandCaller;
import com.intellectualsites.commands.options.ManagerOptions;
import com.intellectualsites.commands.permission.AdvancedPermission;
import com.intellectualsites.commands.permission.SimplePermission;
import com.intellectualsites.commands.util.StringUtil;
import com.intellectualsites.rectangular.parser.InstantArray;
import com.intellectualsites.rectangular.parser.ParserResult;
import com.intellectualsites.rectangular.parser.Parserable;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * This manages all commands, and
 * as such; it is the manager. It
 * should be simple enough to understand
 *
 * @author Sauilitired
 */
public class CommandManager {

    private static final Pattern PATTERN_ON_SPACE = Pattern.compile(" ", Pattern.LITERAL);

    private final Map<String, String> metaMap;
    private final ManagerOptions managerOptions;
    private final Map<String, Command> commands;
    private final Map<String, String> aliasMapping;
    private Character initialCharacter;

    @Setter
    @Getter
    private ImmutableCollection<String> ignoreList;

    /**
     * Default constructor,
     * will use "/" as the
     * command prefix
     */
    public CommandManager() {
        this('/', new ArrayList<Command>());
    }

    /**
     * Constructor which allows you
     * to specify the command
     * prefix, without reequiring
     * a pre made list of commands
     * @param initialCharacter Command prefix
     */
    public CommandManager(Character initialCharacter) {
        this(initialCharacter, null);
    }

    /**
     * Constructor
     * @param initialCharacter Command prefix
     * @param commands Pre made list of commands
     */
    public CommandManager(Character initialCharacter, List<Command> commands) {
        this.managerOptions = new ManagerOptions();
        this.commands = new ConcurrentHashMap<String, Command>();
        this.aliasMapping = new ConcurrentHashMap<String, String>();
        this.metaMap = new HashMap<String, String>();
        this.initialCharacter = initialCharacter;
        if (commands != null) {
            for (Command command : commands) {
                addCommand(command);
            }
        }
        this.setMeta("environment", "standalone");
    }

    final public void addCommand(final Command command) {
        if (ignoreList != null && ignoreList.contains(command.getCommand())) {
            return; // It was ignored!
        }
        if (command.getInitialCharacter() == null) {
            command.setInitialCharacter(getInitialCharacter());
            command.getManagerOptions().setRequirePrefix(false);
        }
        this.commands.put(command.getCommand().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            aliasMapping.put(alias.toLowerCase(), command.getCommand().toLowerCase());
        }
    }

    final public boolean createCommand(final Command command) {
        try {
            command.create();
        } catch(final Exception e) {
            e.printStackTrace();
            return false;
        }
        if (command.getCommand() != null) {
            this.addCommand(command);
            return true;
        }
        return false;
    }

    final public List<Command> getCommands() {
        return new ArrayList<>(this.commands.values());
    }

    final public CommandResult handle(CommandCaller caller, String input) {
        CommandResult.CommandResultBuilder commandResultBuilder = new CommandResult.CommandResultBuilder();
        commandResultBuilder.setCaller(caller);
        commandResultBuilder.setManager(this);
        commandResultBuilder.setInput(input);
        scope:
        {
            // If we want to use the prefix, then this will make sure it's there
            // Could be used to separate commands from chat, etc
            if (managerOptions.getRequirePrefix() && initialCharacter != null && !StringUtil.startsWith(initialCharacter, input)) {
                commandResultBuilder.setCommandResult(CommandHandlingOutput.NOT_COMMAND);
                break scope;
            }
            // If there is an initial character set, and it's present - remove it
            if (initialCharacter != null && StringUtil.startsWith(initialCharacter, input)) {
                input = StringUtil.replaceFirst(initialCharacter, input);
            }
            // Now let's split the command up
            // into labels and arguments
            String[] parts = PATTERN_ON_SPACE.split(input);
            String[] args;
            String command = parts[0].toLowerCase();

            // Let's fetch the command
            Command cmd = null;
            if (commands.containsKey(command)) {
                cmd = commands.get(command);
            } else if (aliasMapping.containsKey(command)) {
                cmd = commands.get(aliasMapping.get(command));
            }

            Map<String, Object> valueMapping = new LinkedHashMap<String, Object>();
            boolean contextFetched = false;

            if (cmd == null) commandFetch: {
                if (parts.length > 1) {
                    String tempContext = command;
                    command = parts[1].toLowerCase();

                    if (commands.containsKey(command)) {
                        cmd = commands.get(command);
                    } else if (aliasMapping.containsKey(command)) {
                        cmd = commands.get(aliasMapping.get(command));
                    }

                    if (cmd != null && cmd.hasContext()) {
                        Object val = cmd.getContext().parse(tempContext);
                        valueMapping.put(cmd.getContext().getName(), val);
                        contextFetched = true;
                        break commandFetch;
                    }
                }
                commandResultBuilder.setCommandResult(CommandHandlingOutput.NOT_FOUND);
                break scope;
            }

            commandResultBuilder.setCommand(cmd);

            if (parts.length == 1) {
                args = new String[0];
            } else {
                if (!contextFetched) {
                    args = new String[parts.length - 1];
                    System.arraycopy(parts, 1, args, 0, args.length);
                } else {
                    args = new String[parts.length - 2];
                    System.arraycopy(parts, 2, args, 0, args.length);
                }
            }

            if (!cmd.getRequiredType().isInstance(caller.getSuperCaller())) {
                commandResultBuilder.setCommandResult(CommandHandlingOutput.CALLER_OF_WRONG_TYPE);
                break scope;
            }

            boolean permitted;
            if (managerOptions.getUseAdvancedPermissions()) {
                permitted = AdvancedPermission.getAdvancedPermission(cmd, managerOptions.getPermissionChecker()).isPermitted(caller);
            } else {
                permitted = SimplePermission.getSimplePermission(cmd).isPermitted(caller);
            }
            if (!permitted) {
                commandResultBuilder.setCommandResult(CommandHandlingOutput.NOT_PERMITTED);
                break scope;
            }
            // Now the fun stuff is beginning :D
            Map<Integer, String> order = cmd.getOrder();
            Map<String, Parserable> requiredArguments = cmd.getRequiredArguments();
            if (requiredArguments.size() > 0) {
                boolean success = true;
                if (args.length < requiredArguments.size()) {
                    success = false;
                } else {
                    int index = 0;
                    for (int orderIndex = Integer.MAX_VALUE; orderIndex > Integer.MAX_VALUE - order.size(); orderIndex--) {
                        String name = order.get(orderIndex);
                        Parserable parserable = requiredArguments.get(name);
                        Object value;
                        if (parserable.getParser() instanceof InstantArray) {
                            StringBuilder cache = new StringBuilder();
                            for (int i = index; i < args.length; i++) {
                                if (cache.toString().isEmpty()) {
                                    cache.append(args[i]);
                                } else {
                                    cache.append(" ").append(args[i]);
                                }
                            }
                            value = cache.toString();
                        } else {
                            // value = parserable.parse(args[index++]);
                            ParserResult parserResult = parserable.parse(args[index++]);
                            if (parserResult.isParsed()) {
                                value = parserResult.getResult();
                            } else {
                                commandResultBuilder.setCommandResult(CommandHandlingOutput.ARGUMENT_ERROR);
                                commandResultBuilder.setCommandArgumentError(new CommandArgumentError(parserResult, parserable));
                                break scope;
                            }
                        }
                        if (value == null) {
                            success = false;
                            break;
                        } else {
                            valueMapping.put(name, value);
                        }
                    }
                }
                if (!success) {
                    ImmutableCollection.Builder<Parserable> builder = new ImmutableList.Builder<>();
                    for (int orderIndex = Integer.MAX_VALUE; orderIndex > Integer.MAX_VALUE - order.size(); orderIndex--) {
                        String name = order.get(orderIndex);
                        builder.add(requiredArguments.get(name));
                    }
                    caller.sendRequiredArgumentsList(this, cmd, builder.build(), cmd.getUsage());
                    commandResultBuilder.setCommandResult(CommandHandlingOutput.WRONG_USAGE);
                    break scope;
                }
            }
            try {
                boolean a = cmd.onCommand(caller, args, valueMapping);
                if (!a) {
                    String usage = cmd.getUsage();
                    if (usage != null && !usage.isEmpty()) {
                        caller.message(getManagerOptions().getUsageFormat().replace("%usage", usage));
                    }
                    commandResultBuilder.setCommandResult(CommandHandlingOutput.WRONG_USAGE);
                    break scope;
                }
            } catch (final Throwable t) {
                commandResultBuilder.setStacktrace(t);
                commandResultBuilder.setCommandResult(CommandHandlingOutput.ERROR);
                break scope;
            }
            commandResultBuilder.setCommandResult(CommandHandlingOutput.SUCCESS);
        }
        return commandResultBuilder.build();
    }

    final public ManagerOptions getManagerOptions() {
        return this.managerOptions;
    }

    final public boolean hasMeta(String key) {
        return this.metaMap.containsKey(key);
    }

    final public void removeMeta(String key) {
        this.metaMap.remove(key);
    }

    final public String getMeta(String key) {
        return this.metaMap.get(key);
    }

    final public void setMeta(String key, String value) {
        if (value == null && hasMeta(key)) {
            removeMeta(key);
        }
        this.metaMap.put(key, value);
    }

    final public Character getInitialCharacter() {
        return this.initialCharacter;
    }

    public void setInitialCharacter(Character initialCharacter) {
        this.initialCharacter = initialCharacter;
    }
}
