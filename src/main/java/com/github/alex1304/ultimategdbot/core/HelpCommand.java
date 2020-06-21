package com.github.alex1304.ultimategdbot.core;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.bold;
import static com.github.alex1304.ultimategdbot.api.util.Markdown.code;
import static com.github.alex1304.ultimategdbot.api.util.Markdown.codeBlock;
import static com.github.alex1304.ultimategdbot.api.util.Markdown.underline;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static reactor.function.TupleUtils.consumer;
import static reactor.function.TupleUtils.function;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.Command;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.CommandProvider;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;
import reactor.util.function.Tuples;

@CommandDescriptor(
		aliases = { "help", "manual" },
		shortDescription = "tr:CoreStrings/help_desc"
)
class HelpCommand {
	
	@CommandAction
	@CommandDoc("tr:CoreStrings/help_run")
	public Mono<Void> run(Context ctx, @Nullable String command, @Nullable String subcommand) {
		return command == null ? displayCommandList(ctx) : displayCommandDocumentation(ctx, command.toLowerCase(), subcommand);
	}

	private static Mono<Void> displayCommandList(Context ctx) {
		var sb = new StringBuilder(ctx.translate("CoreStrings", "command_list", ctx.prefixUsed()) + "\n\n");
		var commandService = ctx.bot().service(CommandService.class);
		return ctx.event().getMessage().getChannel()
				.flatMap(channel -> Flux.fromIterable(commandService.getCommandProviders())
						.sort(comparing(CommandProvider::getName))
						.concatMap(commandProvider -> Flux.fromIterable(commandProvider.getProvidedCommands())
								.filter(cmd -> cmd.getScope().isInScope(channel))
								.filterWhen(cmd -> commandService.getPermissionChecker().isGranted(cmd.getRequiredPermission(), ctx))
								.filterWhen(cmd -> commandService.getPermissionChecker().isGranted(cmd.getMinimumPermissionLevel(), ctx))
								.collectSortedList(comparing(HelpCommand::joinAliases))
								.map(cmdList -> Tuples.of(commandProvider.getName(), cmdList)))
						.doOnNext(consumer((pluginName, cmdList) -> {
							sb.append(bold(underline(pluginName))).append("\n");
							cmdList.stream()
									.forEach(cmd -> {
										var doc = cmd.getDocumentation(ctx.getLocale());
										if (!doc.isHidden()) {
											sb.append(code(ctx.prefixUsed() + joinAliases(cmd)));
											sb.append(" - ");
											sb.append(doc.getShortDescription());
											sb.append('\n');
										}
									});
							sb.append('\n');
						})).then())
				.then(Mono.defer(() -> ctx.bot().service(InteractiveMenuService.class)
						.createPaginated(sb.toString(), 1990)
						.open(ctx)));
	}
	
	private static Mono<Void> displayCommandDocumentation(Context ctx, String commandName, String subcommand) {
		var selectedSubcommand = subcommand == null ? "" : subcommand.toLowerCase();
		var command = new AtomicReference<Command>();
		return Mono.justOrEmpty(ctx.bot().service(CommandService.class).getCommandByAlias(commandName))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("CoreStrings", "error_command_not_found", commandName))))
				.doOnNext(command::set)
				.flatMap(cmd -> findAvailableSubcommands(cmd, ctx).collectList().map(subcommands -> Tuples.of(subcommands, cmd)))
				.flatMap(function((subcommands, cmd) -> {
					var formattedSubcommands = subcommands.stream()
							.map(subcmd -> code(ctx.prefixUsed() + "help " + commandName + (subcmd.isEmpty() ? "" : " " + subcmd)))
							.collect(joining("\n"));
					if (subcommands.contains(selectedSubcommand)) {
						return Mono.just(cmd);
					}
					if (!selectedSubcommand.isEmpty()) {
						return Mono.error(new CommandFailedException(
								ctx.translate("CoreStrings", "error_subcommand_not_found", selectedSubcommand, commandName) + '\n'
										+ "Available subcommands:\n" + formattedSubcommands));
					}
					return Mono.error(new CommandFailedException(
							ctx.translate("CoreStrings", "error_subcommand_required", commandName) + '\n'
									+ formattedSubcommands));
				}))
				.map(cmd -> formatDoc(
						ctx,
						cmd,
						ctx.prefixUsed(),
						ctx.bot().service(CommandService.class).getFlagPrefix(),
						commandName,
						selectedSubcommand))
				.flatMap(doc -> ctx.bot().service(InteractiveMenuService.class)
						.createPaginated(doc, 1200)
						.open(ctx));
	}
	
	private static Flux<String> findAvailableSubcommands(Command cmd, Context ctx) {
		return Flux.fromIterable(cmd.getDocumentation(ctx.getLocale()).getEntries().keySet());
	}
	
	private static String joinAliases(Command cmd) {
		return cmd.getAliases().stream()
				.sorted((a, b) -> a.length() - b.length() == 0 ? a.compareTo(b) : a.length() - b.length())
				.collect(Collectors.joining("|"));
	}
	
	private static String formatDoc(Translator tr, Command cmd, String prefix, String flagPrefix, String selectedCommand, String selectedSubcommand) {
		var doc = cmd.getDocumentation(tr.getLocale());
		var entry = doc.getEntries().get(selectedSubcommand);
		var sb = new StringBuilder(code(prefix + selectedCommand))
				.append(" - ")
				.append(doc.getShortDescription())
				.append(selectedSubcommand.isEmpty() ? "" : "\n" + tr.translate("CoreStrings", "subcommand") + " " + code(selectedSubcommand))
				.append("\n\n")
				.append(bold(underline(tr.translate("CoreStrings", "syntax"))))
				.append("\n")
				.append(codeBlock(prefix + joinAliases(cmd) + (selectedSubcommand.isEmpty() ? "" : " " + selectedSubcommand) + " " + entry.getSyntax()))
				.append(entry.getDescription())
				.append("\n");
		if (!entry.getFlagInfo().isEmpty()) {
			sb.append("\n").append(bold(underline(tr.translate("CoreStrings", "flags")))).append("\n");
			entry.getFlagInfo().forEach((name, info) -> {
				sb.append(code(flagPrefix + name + (info.getValueFormat().isBlank() ? "" : "=<" + info.getValueFormat() + ">")))
						.append(": ")
						.append(info.getDescription())
						.append("\n");
			});
		}
		if (doc.getEntries().size() > 1) {
			sb.append("\n").append(bold(underline(tr.translate("CoreStrings", "see_also")))).append("\n");
			doc.getEntries().forEach((otherPage, otherEntry) -> {
				if (otherPage.equals(selectedSubcommand)) {
					return;
				}
				sb.append(code(prefix + "help " + selectedCommand + (otherPage.isEmpty() ? "" : " " + otherPage)))
						.append(": ")
						.append(extractFirstSentence(otherEntry.getDescription()))
						.append("\n");
			});
		}
		return sb.toString();
	}
	
	private static String extractFirstSentence(String text) {
		var parts = text.split("\\.", 2);
		return (parts.length == 0 ? "" : parts[0]) + ".";
	}
}
