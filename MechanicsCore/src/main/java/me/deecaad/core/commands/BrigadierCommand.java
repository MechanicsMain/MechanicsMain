package me.deecaad.core.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.deecaad.core.MechanicsCore;
import me.deecaad.core.commands.arguments.LiteralArgumentType;
import me.deecaad.core.commands.arguments.MultiLiteralArgumentType;
import me.deecaad.core.commands.arguments.StringArgumentType;
import me.deecaad.core.compatibility.CompatibilityAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

public class BrigadierCommand implements Command<Object> {

    private final CommandBuilder builder;

    public BrigadierCommand(CommandBuilder builder) {
        this.builder = builder;

        CommandDispatcher<Object> dispatcher = CompatibilityAPI.getCommandCompatibility().getCommandDispatcher();
        LiteralArgumentBuilder<Object> result = literal(builder.label).requires(builder.requirements());
        if (builder.args.isEmpty())
            result.executes(this);
        else
            result.then(buildArguments(builder.args));

        dispatcher.register(result);
        MechanicsCore.debug.debug("Registering Command: " + builder);
    }

    @Override
    public int run(CommandContext<Object> context) throws CommandSyntaxException {
        CommandSender sender = CompatibilityAPI.getCommandCompatibility().getCommandSender(context);

        // Ensure that the CommandSender is the proper type. This is typically
        // used for Player only commands, but it may also be used for console
        // or command-block only commands, for example.
        if (!builder.executor.getExecutor().isInstance(sender)) {
            sender.sendMessage(ChatColor.RED + builder.label + " is a " + builder.executor.getExecutor().getSimpleName() + " only command.");
            return -1;
        }

        try {
            //noinspection unchecked
            ((CommandExecutor<CommandSender>) builder.executor).execute(sender, parseBrigadierArguments(context));
            return 0;
        } catch (CommandSyntaxException ex) {
            throw ex;
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Some error occurred whilst executing command. Check console for error. ");
            ex.printStackTrace();
            return -1;
        }
    }

    private Object[] parseBrigadierArguments(CommandContext<Object> context) throws Exception {
        List<Object> temp = new ArrayList<>(builder.args.size());
        for (Argument<?> argument : builder.args)
            if (argument.isListed())
                temp.add(argument.parse(context, argument.getName()));

        Collections.addAll(temp, builder.optionalDefaultValues);
        return temp.toArray();
    }

    private ArgumentBuilder<Object, ?> buildArguments(List<Argument<Object>> args) {
        if (args == null || args.isEmpty())
            throw new IllegalArgumentException("empty args");

        ArgumentBuilder<Object, ?> builder = null;
        ArgumentBuilder<Object, ?> temp;
        for (int i = args.size() - 1; i >= 0; i--) {
            Argument<?> argument = args.get(i);

            // LiteralArgumentTypes are just hard-coded string values, which
            // are usually used as sub-commands. These must be registered
            if (argument.getType() instanceof LiteralArgumentType literal) {
                temp = literal(literal.getLiteral());
            }

            // All other argument types can be parsed normally
            else {
                RequiredArgumentBuilder<Object, ?> required = argument(argument.getName(), argument.getType().getBrigadierType());

                // Build the 3 different suggestion providers. Depending on whether the argument
                // replaces/adds suggestions, some of these may be ignored.
                final SuggestionProvider<Object> def = (context, suggestions) -> argument.getType().suggestions(context, suggestions);
                final SuggestionProvider<Object> custom = buildSuggestionProvider(argument);
                final SuggestionProvider<Object> label = (context, suggestions) -> suggestions.suggest("<" + argument.getName() + ">", argument.description == null ? null : new LiteralMessage(argument.description)).buildFuture();

                SuggestionProvider<Object> combined = (context, suggestions) -> {

                    List<CompletableFuture<Suggestions>> all = new ArrayList<>(3);
                    if (argument.isReplaceSuggestions)
                        all.add(custom.getSuggestions(context, suggestions));
                    else if (argument.suggestions != null)
                        all.addAll(Arrays.asList(def.getSuggestions(context, suggestions), custom.getSuggestions(context, suggestions)));
                    else
                        all.add(def.getSuggestions(context, suggestions));

                    // Only add label when the argument needs it
                    if (argument.getType().includeName())
                        all.add(label.getSuggestions(context, suggestions));

                    CompletableFuture<Suggestions> result = new CompletableFuture<>();
                    CompletableFuture
                            .allOf(all.toArray(new CompletableFuture[0]))
                            .thenRun(() -> {
                                List<Suggestions> list = all.stream().map(CompletableFuture::join).collect(Collectors.toList());
                                Suggestions merged = Suggestions.merge(context.getInput(), list);
                                result.complete(merged);
                            });
                    return result;
                };

                required.suggests(combined);
                temp = required;
            }

            if (builder == null)
                temp.requires(argument.requirements()).executes(this);
            else
                temp.requires(argument.requirements()).then(builder);

            builder = temp;
        }
        return builder;
    }

    private SuggestionProvider<Object> buildSuggestionProvider(Argument<?> argument) {
        return (CommandContext<Object> context, SuggestionsBuilder builder) -> {
            CommandSender sender = CompatibilityAPI.getCommandCompatibility().getCommandSender(context);
            List<Object> previousArguments = new ArrayList<>();

            for (Argument<?> arg : this.builder.args) {
                if (arg == argument)
                    break;

                if (arg.isListed())
                    previousArguments.add(arg.parse(context, arg.getName()));
            }

            CommandData data = new CommandData(sender, previousArguments.toArray(), builder.getInput(), builder.getRemaining());

            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Tooltip suggestion : argument.suggestions == null ? new Tooltip[0] : argument.suggestions.apply(data)) {
                if (suggestion.suggestion().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    Message tooltipMsg = suggestion.tip() == null ? null : new LiteralMessage(suggestion.tip());
                    builder.suggest(suggestion.suggestion(), tooltipMsg);
                }
            }
            return builder.buildFuture();
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void register(CommandBuilder builder) {

        // Now we need to "unpack" the command. This means converting the
        // CommandBuilder format into a brigadier command format.
        // 1. Aliases do not exist in brigadier. We need to register multiple commands
        // 2. Aliases do not exist for sub-commands, we need to register multiple sub-commands for each sub-command.
        // 3. Sub-commands do not exist. Instead, sub-commands are registered as "LiteralArguments" (which are just strings).
        // 4. Arguments are registered as RequiredArguments
        // 5. Optional arguments do not exist. Instead, we must register an additional command for each argument.

        // Used with HelpCommandBuilder
        if (builder.friend != null)
            register(builder.friend);

        // This handles '3', sub-commands need to be converted into "multi-literals".
        if (!builder.subcommands.isEmpty()) {
            for (CommandBuilder subcommand : builder.subcommands) {
                unPack(builder.clone(), new ArrayList<>(), subcommand);
            }
        }

        // For each literal in a multi-literal, we need to register a command
        // for it. Sometimes an argument will have multiple multi-literals,
        // like /wm multi multi args => /wm test explosion args. We use
        // recursion to handle this.
        for (int i = 0; i < builder.args.size(); i++) {
            Argument<?> arg = builder.args.get(i);

            if (arg.getType() instanceof MultiLiteralArgumentType) {
                for (String literal : ((MultiLiteralArgumentType) arg.getType()).getLiterals()) {
                    List<Argument<Object>> copy = new ArrayList<>(builder.args);

                    // Replace the multi-literal with the literal
                    Argument<?> replace = new Argument<>(literal, new LiteralArgumentType(literal))
                            .withPermission(arg.permission)
                            .withRequirements(arg.requirements)
                            .setListed(arg.listed);

                    copy.set(i, (Argument<Object>) replace);
                    CommandBuilder clone = builder.clone();
                    clone.args = copy;
                    register(clone);
                }

                return;
            }

            // StringArgumentType's allow for additional 'literal constants' to
            // be added. WeaponMechanics uses this for *, **, and *r in the
            // /wm give @p * command.
            else if (arg.getType() instanceof StringArgumentType) {
                StringArgumentType type = (StringArgumentType) arg.getType();
                if (type.getLiterals() == null || type.getLiterals().isEmpty())
                    continue;

                for (LiteralArgumentType literal : type.getLiterals()) {

                    List<Argument<?>> copy = new ArrayList<>(builder.args);
                    copy.set(i, new Argument<>(literal.getLiteral(), literal));
                    CommandBuilder clone = builder.clone();
                    clone.args = (List<Argument<Object>>) (List) copy;
                    register(clone);
                }
            }
        }

        // This handles '5', optional arguments need to generate new commands.
        // Consider the command: /wm give CJCafter AK-47 1 scope_attachment
        // 'CJCrafter' and 'AK-47' are required arguments. '1' and
        // 'scope_attachment' are not required arguments.
        // In order to allow optional arguments, we must register each command:
        // /wm give CJCrafter AK-47
        // /wm give CJCrafter AK-47 1
        // /wm give CJCrafter AK-47 1 scope_attachment
        for (int i = builder.args.size() - 1; i >= 0; i--) {
            Argument<?> arg = builder.args.get(i);

            if (!arg.isRequired()) {
                CommandBuilder clone = builder.clone();
                clone.args = new ArrayList<>(clone.args.subList(0, i));

                Object[] defaults = new Object[clone.optionalDefaultValues.length + 1];
                System.arraycopy(clone.optionalDefaultValues, 0, defaults, 1, defaults.length - 1);
                defaults[0] = arg.getDefaultValue();
                clone.optionalDefaultValues = defaults;

                register(clone);
                break;
            }
        }

        if (builder.executor == null) {
            MechanicsCore.debug.warn("No executor for: " + builder);
            return;
        }

        if (builder.friend != null)
            register(builder.friend);

        new BrigadierCommand(builder.clone());

        // Now we need to handle aliases. Simply register each alias as if they
        // are the main command. Brigadier does seem to have a "redirect system",
        // but it is complicated.
        for (String alias : builder.aliases) {
            CommandBuilder clone = builder.clone();
            clone.label = alias;

            new BrigadierCommand(clone);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void unPack(CommandBuilder root, List<Argument<?>> arguments, CommandBuilder subcommand) {

        MultiLiteralArgumentType literals = new MultiLiteralArgumentType(subcommand.label, subcommand.aliases.toArray(new String[0]));
        Argument<?> argument = new Argument<>("sub-command", literals)
                .withPermission(subcommand.permission)
                .withRequirements(subcommand.requirements)
                .setListed(false);

        arguments.add(argument);

        // When we reach the outer-most branch of the tree, there must be an
        // executor (Otherwise the command won't work). TODO add validation.
        if (subcommand.executor != null) {
            CommandBuilder temp = root.clone();
            temp.args = (List) arguments;
            temp.withArguments((List) subcommand.args);
            temp.executes(subcommand.executor);

            temp.subcommands = new ArrayList<>();
            register(temp);

            if (subcommand.friend != null) {
                for (int i = arguments.size() - 1; i >= 0; i--) {
                    Argument<?> arg = arguments.get(i);

                    if (arg.getName().equals("sub-command"))
                        break;
                    if (!arg.isRequired())
                        continue;

                    CommandBuilder help = root.clone();
                    help.args = (List) arguments.subList(0, i);
                    help.executes(subcommand.friend.executor);

                    help.subcommands = new ArrayList<>();
                    register(help);
                }
            }
        }

        // Flatten all subcommands
        for (CommandBuilder subsubcommand : subcommand.subcommands)
            unPack(root.clone(), new ArrayList<>(arguments), subsubcommand);
    }
}
