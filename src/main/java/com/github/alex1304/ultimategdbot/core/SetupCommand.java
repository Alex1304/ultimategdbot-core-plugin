package com.github.alex1304.ultimategdbot.core;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.bold;
import static com.github.alex1304.ultimategdbot.api.util.Markdown.underline;
import static java.text.Collator.SECONDARY;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.text.Collator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.Scope;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;
import com.github.alex1304.ultimategdbot.api.command.menu.MessageMenuInteraction;
import com.github.alex1304.ultimategdbot.api.command.menu.UnexpectedReplyException;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.BooleanConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.ConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.ConfigEntryVisitor;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildMemberConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildRoleConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.IntegerConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.LongConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.StringConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.ValidationException;
import com.github.alex1304.ultimategdbot.api.util.DiscordFormatter;
import com.github.alex1304.ultimategdbot.api.util.DiscordParser;
import com.github.alex1304.ultimategdbot.api.util.Markdown;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;

@CommandDescriptor(
	aliases = { "setup", "settings", "configure", "config" },
	shortDescription = "tr:strings_core/setup_desc",
	scope = Scope.GUILD_ONLY
)
@CommandPermission(level = PermissionLevel.GUILD_ADMIN)
class SetupCommand {

	@CommandAction
	@CommandDoc("tr:strings_core/setup_run")
	public Mono<Void> run(Context ctx) {
		return ctx.bot().service(DatabaseService.class)
				.configureGuild(ctx, ctx.event().getGuildId().orElseThrow())
				.collectList()
				.flatMap(configurators -> {
					var formattedConfigs = new ArrayList<Mono<String>>();
					var formattedValuePerEntry = new HashMap<ConfigEntry<?>, String>();
					for (var configurator : configurators) {
						var formattedEntries = new ArrayList<Mono<String>>();
						formattedEntries.add(Mono.just(bold(underline(configurator.getName()))));
						for (var entry : configurator.getConfigEntries()) {
							formattedEntries.add(entry.accept(new DisplayVisitor(ctx))
									.defaultIfEmpty("none")
									.doOnNext(displayValue -> formattedValuePerEntry.put(entry, displayValue))
									.map(displayValue -> bold(entry.getDisplayName() + ':') + ' ' + displayValue)
									.map(Markdown::quote));
						}
						formattedConfigs.add(Flux.concat(formattedEntries)
								.collect(joining("\n")));
					}
					formattedConfigs.add(Mono.just(ctx.translate("strings_core", "react")));
					return Flux.concat(formattedConfigs)
							.collect(joining("\n\n"))
							.map(content -> Tuples.of(configurators, content, formattedValuePerEntry));
				})
				.flatMap(TupleUtils.function((configurators, content, formattedValuePerEntry) -> ctx.bot()
						.service(InteractiveMenuService.class)
						.createPaginated(content, 1000)
						.addReactionItem("📝", editInteraction -> {
							editInteraction.closeMenu();
							return handleEditInteraction(ctx, configurators, formattedValuePerEntry, false);
						})
						.addReactionItem("🔄", resetInteraction -> {
							resetInteraction.closeMenu();
							return handleEditInteraction(ctx, configurators, formattedValuePerEntry, true);
						})
						.addReactionItem(ctx.bot().service(InteractiveMenuService.class)
								.getPaginationControls()
								.getCloseEmoji(), interaction -> Mono.fromRunnable(interaction::closeMenu))
						.deleteMenuOnClose(true)
						.open(ctx)));
	}

	private static Mono<Void> handleEditInteraction(Context ctx, List<GuildConfigurator<?>> configurators,
			Map<ConfigEntry<?>, String> formattedValuePerEntry, boolean reset) {
		var sb = new StringBuilder("**");
		if (reset) {
			sb.append(ctx.translate("strings_core", "prompt_feature_reset"));
		} else {
			sb.append(ctx.translate("strings_core", "prompt_feature_setup"));
		}
		sb.append("**\n\n");
		var i = 1;
		for (var configurator : configurators) {
			sb.append(Markdown.code(i + ""))
					.append(": ")
					.append(Markdown.bold(configurator.getName()))
					.append(" - ")
					.append(configurator.getDescription())
					.append('\n');
			i++;
		}
		return ctx.bot().service(InteractiveMenuService.class)
				.createPaginated(sb.toString(), 1000)
				.addMessageItem("", selectInteraction -> {
					int selected;
					try {
						selected = Integer.parseInt(selectInteraction.getArgs().get(0));
					} catch (NumberFormatException e) {
						return Mono.error(new UnexpectedReplyException(ctx.translate("strings_core", "error_invalid_input")));
					}
					if (selected < 1 || selected > configurators.size()) {
						return Mono.error(new UnexpectedReplyException(ctx.translate("strings_core", "error_invalid_input", selected)));
					}
					selectInteraction.closeMenu();
					var configurator = configurators.get(selected - 1);
					if (reset) {
						return ctx.bot().service(InteractiveMenuService.class)
								.create(Markdown.bold(ctx.translate("strings_core", "reset_confirm", configurator.getName())))
								.addReactionItem("✅", interaction -> {
									return configurator.resetConfig(ctx.bot().service(DatabaseService.class))
											.then(ctx.reply("✅ " + ctx.translate("strings_core", "reset_success")))
											.then();
								})
								.addReactionItem(ctx.bot().service(InteractiveMenuService.class)
										.getPaginationControls()
										.getCloseEmoji(), interaction -> Mono.fromRunnable(interaction::closeMenu))
								.open(ctx); 
					}
					return handleSelectedFeatureInteraction(ctx, selectInteraction, configurator, formattedValuePerEntry);
				})
				.deleteMenuOnClose(true)
				.deleteMenuOnTimeout(true)
				.open(ctx);
	}
	
	private static Mono<Void> handleSelectedFeatureInteraction(Context ctx, MessageMenuInteraction selectInteraction,
			GuildConfigurator<?> configurator, Map<ConfigEntry<?>, String> formattedValuePerEntry) {
		var entries = configurator.getConfigEntries().stream()
				.filter(not(ConfigEntry::isReadOnly))
				.collect(toUnmodifiableList());
		if (entries.isEmpty()) {
			return Mono.error(new CommandFailedException(ctx.translate("strings_core", "error_nothing_to_configure")));
		}
		var entryQueue = new ArrayDeque<>(entries);
		var firstEntry = entryQueue.element();
		var valueOfFirstEntry = formattedValuePerEntry.get(firstEntry);
		return firstEntry.accept(new PromptVisitor(ctx, configurator, valueOfFirstEntry))
				.map(ctx.bot().service(InteractiveMenuService.class)::create)
				.flatMap(menu -> menu
						.addReactionItem("⏭️", interaction -> goToNextEntry(ctx, entryQueue, formattedValuePerEntry,
								configurator, interaction.getMenuMessage(), interaction::closeMenu))
						.addReactionItem("🔄", interaction -> entryQueue.element().setValue(null)
								.then(goToNextEntry(ctx, entryQueue, formattedValuePerEntry, configurator,
										interaction.getMenuMessage(), interaction::closeMenu)))
						.addReactionItem("✅", interaction -> endConfiguration(configurator, ctx, interaction::closeMenu))
						.addReactionItem("🚫", __ -> Mono.error(new CommandFailedException(
								ctx.translate("strings_core", "error_configuration_cancelled"))))
						.addMessageItem("", interaction -> {
							var input = interaction.getEvent().getMessage().getContent();
							var currentEntry = entryQueue.element();
							var editEntry = currentEntry.accept(new EditVisitor(ctx, input)).onErrorMap(ValidationException.class,
									e -> new UnexpectedReplyException(ctx.translate("strings_core", "error_constraint_violation")
											+ ' ' + e.getMessage()));
							return editEntry.then(goToNextEntry(ctx, entryQueue, formattedValuePerEntry, configurator,
									interaction.getMenuMessage(), interaction::closeMenu));
						})
						.deleteMenuOnClose(true)
						.deleteMenuOnTimeout(true)
						.closeAfterMessage(false)
						.closeAfterReaction(false)
						.open(ctx));
	}

	private static Mono<Void> goToNextEntry(Context ctx, Queue<ConfigEntry<?>> entryQueue,
			Map<ConfigEntry<?>, String> formattedValuePerEntry, GuildConfigurator<?> configurator, Message menuMessage,
			Runnable menuCloser) {
		var goToNextEntry = Mono.fromCallable(entryQueue::element)
				.flatMap(nextEntry -> nextEntry.accept(new PromptVisitor(ctx, configurator, formattedValuePerEntry.get(nextEntry)))
						.flatMap(prompt -> menuMessage.edit(spec -> spec.setContent(prompt))))
				.then();
		return Mono.fromRunnable(entryQueue::remove)
				.then(Mono.defer(() -> entryQueue.isEmpty()
						? endConfiguration(configurator, ctx, menuCloser)
						: goToNextEntry));
	}
	
	private static Mono<Void> endConfiguration(GuildConfigurator<?> configurator, Context ctx, Runnable menuCloser) {
		return configurator.saveConfig(ctx.bot().service(DatabaseService.class))
				.then(ctx.reply(":white_check_mark: " + ctx.translate("strings_core", "configuration_done"))
						.and(Mono.fromRunnable(menuCloser)));
	}
	
	private static class DisplayVisitor implements ConfigEntryVisitor<String> {
		
		private final Translator tr;
		
		DisplayVisitor(Translator tr) {
			this.tr = tr;
		}

		@Override
		public Mono<String> visit(IntegerConfigEntry entry) {
			return entry.getValue().map(Object::toString);
		}
	
		@Override
		public Mono<String> visit(LongConfigEntry entry) {
			return entry.getValue().map(Object::toString);
		}
	
		@Override
		public Mono<String> visit(BooleanConfigEntry entry) {
			return entry.getValue().map(bool -> tr.translate("strings_common", bool ? "yes" : "no"));
		}
	
		@Override
		public Mono<String> visit(StringConfigEntry entry) {
			return entry.getValue();
		}
	
		@Override
		public Mono<String> visit(GuildChannelConfigEntry entry) {
			return entry.getValue().map(DiscordFormatter::formatGuildChannel);
		}
	
		@Override
		public Mono<String> visit(GuildRoleConfigEntry entry) {
			return entry.getValue().map(DiscordFormatter::formatRole);
		}
	
		@Override
		public Mono<String> visit(GuildMemberConfigEntry entry) {
			return entry.getValue().map(User::getTag);
		}	
	}
	
	private static class PromptVisitor implements ConfigEntryVisitor<String> {

		private final Translator tr;
		private final GuildConfigurator<?> configurator;
		private final String currentValue;
		
		private PromptVisitor(Translator tr, GuildConfigurator<?> configurator, String currentValue) {
			this.tr = tr;
			this.configurator = configurator;
			this.currentValue = currentValue;
		}

		@Override
		public Mono<String> visit(IntegerConfigEntry entry) {
			return Mono.just(promptSet(entry, tr.translate("strings_core", "prompt_numeric")));
		}

		@Override
		public Mono<String> visit(LongConfigEntry entry) {
			return Mono.just(promptSet(entry, tr.translate("strings_core", "prompt_numeric")));
		}

		@Override
		public Mono<String> visit(BooleanConfigEntry entry) {
			return Mono.just(promptSet(entry, tr.translate("strings_core", "prompt_boolean")));
		}

		@Override
		public Mono<String> visit(StringConfigEntry entry) {
			return Mono.just(promptSet(entry, null));
		}

		@Override
		public Mono<String> visit(GuildChannelConfigEntry entry) {
			return Mono.just(promptSet(entry, tr.translate("strings_core", "prompt_channel")));
		}

		@Override
		public Mono<String> visit(GuildRoleConfigEntry entry) {
			return Mono.just(promptSet(entry, tr.translate("strings_core", "prompt_channel")));
		}

		@Override
		public Mono<String> visit(GuildMemberConfigEntry entry) {
			return Mono.just(promptSet(entry, tr.translate("strings_core", "prompt_member")));
		}
		
		private String promptSet(ConfigEntry<?> entry, String expecting) {
			return Markdown.bold(entry.getDisplayName()) + " (" + configurator.getName() + ")\n\n"
					+ (entry.getDescription().isEmpty() ? "" : entry.getDescription() + "\n\n")
					+ Markdown.bold(tr.translate("strings_core", "current_value")) + ' ' + currentValue + '\n'
					+ tr.translate("strings_core", "react_entry") + '\n'
					+ Markdown.bold(tr.translate("strings_core", "prompt_new_value")
							+ (expecting == null ? "" : " (" + expecting + ")") + ':') + '\n';
		}
	}
	
	private static class EditVisitor implements ConfigEntryVisitor<Void> {
		
		private final Context context;
		private final String input;
		
		private EditVisitor(Context context, String input) {
			this.context = context;
			this.input = input;
		}

		@Override
		public Mono<Void> visit(IntegerConfigEntry entry) {
			int value;
			try {
				value = Integer.parseInt(input);
			} catch (NumberFormatException e) {
				return Mono.error(new UnexpectedReplyException(context.translate("strings_core", "error_invalid_input")));
			}
			return entry.setValue(value);
		}

		@Override
		public Mono<Void> visit(LongConfigEntry entry) {
			long value;
			try {
				value = Long.parseLong(input);
			} catch (NumberFormatException e) {
				return Mono.error(new UnexpectedReplyException(context.translate("strings_core", "error_invalid_input")));
			}
			return entry.setValue(value);
		}

		@Override
		public Mono<Void> visit(BooleanConfigEntry entry) {
			var collator = Collator.getInstance(context.getLocale());
			collator.setStrength(SECONDARY);
			var yes = context.translate("strings_common", "yes");
			var no = context.translate("strings_common", "no");
			var isYes = collator.compare(input, yes) == 0;
			var isNo = collator.compare(input, no) == 0;
			if (!isYes && !isNo) {
				return Mono.error(new UnexpectedReplyException(context.translate("strings_core", "error_expected_boolean")));
			}
			return entry.setValue(isYes);
		}

		@Override
		public Mono<Void> visit(StringConfigEntry entry) {
			return entry.setValue(input);
		}

		@Override
		public Mono<Void> visit(GuildChannelConfigEntry entry) {
			return DiscordParser.parseGuildChannel(context, context.bot(), entry.getGuildId(), input)
					.flatMap(entry::setValue)
					.onErrorMap(IllegalArgumentException.class, e -> new UnexpectedReplyException(e.getMessage()));
		}

		@Override
		public Mono<Void> visit(GuildRoleConfigEntry entry) {
			return DiscordParser.parseRole(context, context.bot(), entry.getGuildId(), input)
					.flatMap(entry::setValue)
					.onErrorMap(IllegalArgumentException.class, e -> new UnexpectedReplyException(e.getMessage()));
		}

		@Override
		public Mono<Void> visit(GuildMemberConfigEntry entry) {
			return DiscordParser.parseUser(context, context.bot(), input)
					.flatMap(user -> user.asMember(entry.getGuildId()))
					.flatMap(entry::setValue)
					.onErrorMap(IllegalArgumentException.class, e -> new UnexpectedReplyException(e.getMessage()));
		}
	}
}
