package com.momo.worldeditsnapshotcommit.extension.platform;

import com.google.common.base.Joiner;
import com.momo.worldeditsnapshotcommit.command.SnapshotUtilCommandsAlt;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandLocals;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.minecraft.util.commands.WrappedCommandException;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.event.platform.CommandSuggestionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.extension.platform.PlatformManager;
import com.sk89q.worldedit.internal.command.*;
import com.sk89q.worldedit.session.request.Request;
import com.sk89q.worldedit.util.command.Dispatcher;
import com.sk89q.worldedit.util.command.InvalidUsageException;
import com.sk89q.worldedit.util.command.fluent.CommandGraph;
import com.sk89q.worldedit.util.command.parametric.ExceptionConverter;
import com.sk89q.worldedit.util.command.parametric.LegacyCommandsHandler;
import com.sk89q.worldedit.util.command.parametric.ParametricBuilder;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.util.formatting.ColorCodeBuilder;
import com.sk89q.worldedit.util.formatting.component.CommandUsageBox;
import com.sk89q.worldedit.util.logging.DynamicStreamHandler;
import com.sk89q.worldedit.util.logging.LogFormat;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handles the registration and invocation of commands.
 *
 * <p>This class is primarily for internal usage.</p>
 */
public final class CommandManagerAlt {

    public static final Pattern COMMAND_CLEAN_PATTERN = Pattern.compile("^[/]+");
    private static final Logger log = Logger.getLogger(CommandManagerAlt.class.getCanonicalName());
    private static final Logger commandLog = Logger.getLogger(CommandManagerAlt.class.getCanonicalName() + ".CommandLog");
    private static final Pattern numberFormatExceptionPattern = Pattern.compile("^For input string: \"(.*)\"$");

    private final WorldEdit worldEdit;
    private final PlatformManager platformManager;
    private final Dispatcher dispatcher;
    private final DynamicStreamHandler dynamicHandler = new DynamicStreamHandler();
    private final ExceptionConverter exceptionConverter;

    /**
     * Create a new instance.
     *
     * @param worldEdit the WorldEdit instance
     */
    CommandManagerAlt(final WorldEdit worldEdit, PlatformManager platformManager) {
        checkNotNull(worldEdit);
        checkNotNull(platformManager);
        this.worldEdit = worldEdit;
        this.platformManager = platformManager;
        this.exceptionConverter = new WorldEditExceptionConverter(worldEdit);

        // Register this instance for command events
        worldEdit.getEventBus().register(this);

        // Setup the logger
        commandLog.addHandler(dynamicHandler);

        // Set up the commands manager
        ParametricBuilder builder = new ParametricBuilder();
        builder.setAuthorizer(new ActorAuthorizer());
        builder.setDefaultCompleter(new UserCommandCompleter(platformManager));
        builder.addBinding(new WorldEditBinding(worldEdit));
        builder.addInvokeListener(new LegacyCommandsHandler());
        builder.addInvokeListener(new CommandLoggingHandler(worldEdit, commandLog));

        dispatcher = new CommandGraph()
                .builder(builder)
                    .commands()
                        .registerMethods(new SnapshotUtilCommandsAlt(worldEdit))
                .getDispatcher();
    }

    public ExceptionConverter getExceptionConverter() {
        return exceptionConverter;
    }

    void register(Platform platform) {
        log.log(Level.FINE, "Registering commands with " + platform.getClass().getCanonicalName());

        LocalConfiguration config = platform.getConfiguration();
        boolean logging = config.logCommands;
        String path = config.logFile;

        // Register log
        if (!logging || path.isEmpty()) {
            dynamicHandler.setHandler(null);
            commandLog.setLevel(Level.OFF);
        } else {
            File file = new File(config.getWorkingDirectory(), path);
            commandLog.setLevel(Level.ALL);

            log.log(Level.INFO, "Logging WorldEdit commands to " + file.getAbsolutePath());

            try {
                dynamicHandler.setHandler(new FileHandler(file.getAbsolutePath(), true));
            } catch (IOException e) {
                log.log(Level.WARNING, "Could not use command log file " + path + ": " + e.getMessage());
            }

            dynamicHandler.setFormatter(new LogFormat(config.logFormat));
        }

        platform.registerCommands(dispatcher);
    }

    void unregister() {
        dynamicHandler.setHandler(null);
    }

    public String[] commandDetection(String[] split) {
        // Quick script shortcut
        if (split[0].matches("^[^/].*\\.js$")) {
            String[] newSplit = new String[split.length + 1];
            System.arraycopy(split, 0, newSplit, 1, split.length);
            newSplit[0] = "cs";
            newSplit[1] = newSplit[1];
            split = newSplit;
        }

        String searchCmd = split[0].toLowerCase();

        // Try to detect the command
        if (!dispatcher.contains(searchCmd)) {
            if (worldEdit.getConfiguration().noDoubleSlash && dispatcher.contains("/" + searchCmd)) {
                split[0] = "/" + split[0];
            } else if (searchCmd.length() >= 2 && searchCmd.charAt(0) == '/' && dispatcher.contains(searchCmd.substring(1))) {
                split[0] = split[0].substring(1);
            }
        }

        return split;
    }

    @Subscribe
    public void handleCommand(CommandEvent event) {
        Request.reset();

        Actor actor = platformManager.createProxyActor(event.getActor());
        String[] split = commandDetection(event.getArguments().split(" "));

        // No command found!
        if (!dispatcher.contains(split[0])) {
            return;
        }

        LocalSession session = worldEdit.getSessionManager().get(actor);
        LocalConfiguration config = worldEdit.getConfiguration();

        CommandLocals locals = new CommandLocals();
        locals.put(Actor.class, actor);
        locals.put("arguments", event.getArguments());

        long start = System.currentTimeMillis();

        try {
            // This is a bit of a hack, since the call method can only throw CommandExceptions
            // everything needs to be wrapped at least once. Which means to handle all WorldEdit
            // exceptions without writing a hook into every dispatcher, we need to unwrap these
            // exceptions and rethrow their converted form, if their is one.
            try {
                dispatcher.call(Joiner.on(" ").join(split), locals, new String[0]);
            } catch (Throwable t) {
                // Use the exception converter to convert the exception if any of its causes
                // can be converted, otherwise throw the original exception
                Throwable next = t;
                do {
                    exceptionConverter.convert(next);
                    next = next.getCause();
                } while (next != null);

                throw t;
            }
        } catch (CommandPermissionsException e) {
            actor.printError("You are not permitted to do that. Are you in the right mode?");
        } catch (InvalidUsageException e) {
            if (e.isFullHelpSuggested()) {
                actor.printRaw(ColorCodeBuilder.asColorCodes(new CommandUsageBox(e.getCommand(), e.getCommandUsed("/", ""), locals)));
                String message = e.getMessage();
                if (message != null) {
                    actor.printError(message);
                }
            } else {
                String message = e.getMessage();
                actor.printError(message != null ? message : "The command was not used properly (no more help available).");
                actor.printError("Usage: " + e.getSimpleUsageString("/"));
            }
        } catch (WrappedCommandException e) {
            Throwable t = e.getCause();
            actor.printError("Please report this error: [See console]");
            actor.printRaw(t.getClass().getName() + ": " + t.getMessage());
            log.log(Level.SEVERE, "An unexpected error while handling a WorldEdit command", t);
        } catch (CommandException e) {
            String message = e.getMessage();
            if (message != null) {
                actor.printError(e.getMessage());
            } else {
                actor.printError("An unknown error has occurred! Please see console.");
                log.log(Level.SEVERE, "An unknown error occurred", e);
            }
        } finally {
            EditSession editSession = locals.get(EditSession.class);

            if (editSession != null) {
                session.remember(editSession);
                editSession.flushQueue();

                if (config.profile) {
                    long time = System.currentTimeMillis() - start;
                    int changed = editSession.getBlockChangeCount();
                    if (time > 0) {
                        double throughput = changed / (time / 1000.0);
                        actor.printDebug((time / 1000.0) + "s elapsed (history: "
                                + changed + " changed; "
                                + Math.round(throughput) + " blocks/sec).");
                    } else {
                        actor.printDebug((time / 1000.0) + "s elapsed.");
                    }
                }

                worldEdit.flushBlockBag(actor, editSession);
            }
        }

        event.setCancelled(true);
    }

    @Subscribe
    public void handleCommandSuggestion(CommandSuggestionEvent event) {
        try {
            CommandLocals locals = new CommandLocals();
            locals.put(Actor.class, event.getActor());
            locals.put("arguments", event.getArguments());
            event.setSuggestions(dispatcher.getSuggestions(event.getArguments(), locals));
        } catch (CommandException e) {
            event.getActor().printError(e.getMessage());
        }
    }

    /**
     * Get the command dispatcher instance.
     *
     * @return the command dispatcher
     */
    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public static Logger getLogger() {
        return commandLog;
    }

}
