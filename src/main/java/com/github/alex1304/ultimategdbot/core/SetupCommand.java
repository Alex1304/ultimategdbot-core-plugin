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
import com.github.alex1304.ultimategdbot.api.command.menu.PageNumberOutOfRangeException;
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
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;

@CommandDescriptor(
	aliases = { "setup", "settings", "configure", "config" },
	shortDescription = "tr:CoreStrings/setup_desc",
	scope = Scope.GUILD_ONLY
)
@CommandPermission(level = PermissionLevel.GUILD_ADMIN)
class SetupCommand {

	@CommandAction
	@CommandDoc("tr:CoreStrings/setup_run")
	public Mono<Void> run(Context ctx) {
		return ctx.bot().service(DatabaseService.class)
				.configureGuild(ctx, ctx.event().getGuildId().orElseThrow())
				.collectList()
				.flatMap(configurators -> {
					var formattedConfigs = new ArrayList<Mono<String>>();
					var formattedValuePerEntry = new HashMap<ConfigEntry<?>, String>();
					for (var configurator : configurators) {
						var formattedEntries = new ArrayList<Mono<String>>();
						formattedEntries.add(Mono.just(bold(underline(configurator.getName())) + '\n'
								+ configurator.getDescription()));
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
					return Flux.concat(formattedConfigs)
							.collect(toUnmodifiableList())
							.map(content -> Tuples.of(configurators, content, formattedValuePerEntry));
				})
				.flatMap(TupleUtils.function((configurators, content, formattedValuePerEntry) -> ctx.bot()
						.service(InteractiveMenuService.class)
						.createPaginated((tr, page) -> {
							PageNumberOutOfRangeException.check(page, 0, content.size() - 1);
							return new MessageSpecTemplate(content.get(page), embed -> embed.addField(
									tr.translate("CommonStrings", "pagination_page_counter", page + 1, content.size()),
									tr.translate("CommonStrings", "pagination_go_to") + '\n'
									+ tr.translate("CoreStrings", "react", "\uD83D\uDCDD", "\uD83D\uDD04"), true));
						})
						.addReactionItem("📝", editInteraction -> {
							editInteraction.closeMenu();
							return handleSelectedFeatureInteraction(ctx, configurators.get(editInteraction.get("currentPage")),
									formattedValuePerEntry);
						})
						.addReactionItem("🔄", resetInteraction -> {
							resetInteraction.closeMenu();
							var configurator = configurators.get(resetInteraction.get("currentPage"));
							return ctx.bot().service(InteractiveMenuService.class)
									.create(Markdown.bold(ctx.translate("CoreStrings", "reset_confirm", configurator.getName())))
									.addReactionItem("✅", interaction -> {
										return configurator.resetConfig(ctx.bot().service(DatabaseService.class))
												.then(ctx.reply("✅ " + ctx.translate("CoreStrings", "reset_success")))
												.then();
									})
									.addReactionItem(ctx.bot().service(InteractiveMenuService.class)
											.getPaginationControls()
											.getCloseEmoji(), interaction -> Mono.fromRunnable(interaction::closeMenu))
									.deleteMenuOnClose(true)
									.open(ctx);
						})
						.addReactionItem(ctx.bot().service(InteractiveMenuService.class)
								.getPaginationControls()
								.getCloseEmoji(), interaction -> Mono.fromRunnable(interaction::closeMenu))
						.deleteMenuOnClose(true)
						.open(ctx)));
	}
	
	private static Mono<Void> handleSelectedFeatureInteraction(Context ctx,
			GuildConfigurator<?> configurator, Map<ConfigEntry<?>, String> formattedValuePerEntry) {
		var entries = configurator.getConfigEntries().stream()
				.filter(not(ConfigEntry::isReadOnly))
				.collect(toUnmodifiableList());
		if (entries.isEmpty()) {
			return Mono.error(new CommandFailedException(ctx.translate("CoreStrings", "error_nothing_to_configure")));
		}
		var entryQueue = new ArrayDeque<>(entries);
		var totalPages = entryQueue.size();
		var firstEntry = entryQueue.element();
		var valueOfFirstEntry = formattedValuePerEntry.get(firstEntry);
		return firstEntry.accept(new PromptVisitor(ctx, valueOfFirstEntry, 1, totalPages))
				.map(ctx.bot().service(InteractiveMenuService.class)::create)
				.flatMap(menu -> menu
						.addReactionItem("⏭️", interaction -> goToNextEntry(ctx, entryQueue, formattedValuePerEntry,
								configurator, interaction.getMenuMessage(), interaction::closeMenu, totalPages))
						.addReactionItem("🔄", interaction -> entryQueue.element().setValue(null)
								.then(goToNextEntry(ctx, entryQueue, formattedValuePerEntry, configurator,
										interaction.getMenuMessage(), interaction::closeMenu, totalPages)))
						.addReactionItem("✅", interaction -> endConfiguration(configurator, ctx, interaction::closeMenu))
						.addReactionItem("🚫", __ -> Mono.error(new CommandFailedException(
								ctx.translate("CoreStrings", "error_configuration_cancelled"))))
						.addMessageItem("", interaction -> {
							var input = interaction.getEvent().getMessage().getContent();
							var currentEntry = entryQueue.element();
							var editEntry = currentEntry.accept(new EditVisitor(ctx, input)).onErrorMap(ValidationException.class,
									e -> new UnexpectedReplyException(ctx.translate("CoreStrings", "error_constraint_violation")
											+ ' ' + e.getMessage()));
							return editEntry.then(goToNextEntry(ctx, entryQueue, formattedValuePerEntry, configurator,
									interaction.getMenuMessage(), interaction::closeMenu, totalPages));
						})
						.deleteMenuOnClose(true)
						.deleteMenuOnTimeout(true)
						.closeAfterMessage(false)
						.closeAfterReaction(false)
						.open(ctx));
	}

	private static Mono<Void> goToNextEntry(Context ctx, Queue<ConfigEntry<?>> entryQueue,
			Map<ConfigEntry<?>, String> formattedValuePerEntry, GuildConfigurator<?> configurator, Message menuMessage,
			Runnable menuCloser, int totalPages) {
		var goToNextEntry = Mono.fromCallable(entryQueue::element)
				.flatMap(nextEntry -> nextEntry.accept(new PromptVisitor(ctx,
								formattedValuePerEntry.get(nextEntry), totalPages - entryQueue.size() + 1, totalPages))
						.flatMap(prompt -> menuMessage.edit(spec -> spec.setContent(prompt))))
				.then();
		return Mono.fromRunnable(entryQueue::remove)
				.then(Mono.defer(() -> entryQueue.isEmpty()
						? endConfiguration(configurator, ctx, menuCloser)
						: goToNextEntry));
	}
	
	private static Mono<Void> endConfiguration(GuildConfigurator<?> configurator, Context ctx, Runnable menuCloser) {
		return configurator.saveConfig(ctx.bot().service(DatabaseService.class))
				.then(ctx.reply(":white_check_mark: " + ctx.translate("CoreStrings", "configuration_done"))
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
			return entry.getValue().map(bool -> tr.translate("CommonStrings", bool ? "yes" : "no"));
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
		private final String currentValue;
		private final int currentPage;
		private final int totalPages;
		
		private PromptVisitor(Translator tr,String currentValue, int currentPage, int totalPages) {
			this.tr = tr;
			this.currentValue = currentValue;
			this.currentPage = currentPage;
			this.totalPages = totalPages;
		}

		@Override
		public Mono<String> visit(IntegerConfigEntry entry) {
			return Mono.just(prompt(entry, tr.translate("CoreStrings", "prompt_numeric")));
		}

		@Override
		public Mono<String> visit(LongConfigEntry entry) {
			return Mono.just(prompt(entry, tr.translate("CoreStrings", "prompt_numeric")));
		}

		@Override
		public Mono<String> visit(BooleanConfigEntry entry) {
			return Mono.just(prompt(entry, tr.translate("CoreStrings", "prompt_boolean")));
		}

		@Override
		public Mono<String> visit(StringConfigEntry entry) {
			return Mono.just(prompt(entry, null));
		}

		@Override
		public Mono<String> visit(GuildChannelConfigEntry entry) {
			return Mono.just(prompt(entry, tr.translate("CoreStrings", "prompt_channel")));
		}

		@Override
		public Mono<String> visit(GuildRoleConfigEntry entry) {
			return Mono.just(prompt(entry, tr.translate("CoreStrings", "prompt_channel")));
		}

		@Override
		public Mono<String> visit(GuildMemberConfigEntry entry) {
			return Mono.just(prompt(entry, tr.translate("CoreStrings", "prompt_member")));
		}
		
		private String prompt(ConfigEntry<?> entry, String expecting) {
			return "───────────────────────\n"
					+ Markdown.bold(entry.getDisplayName()) + " (" + currentPage + '/' + totalPages + ")\n\n"
					+ (entry.getDescription().isEmpty() ? "" : entry.getDescription() + "\n\n")
					+ Markdown.bold(tr.translate("CoreStrings", "current_value")) + ' ' + currentValue + '\n'
					+ tr.translate("CoreStrings", "react_entry", "\u23ED\uFE0F", "\uD83D\uDD04", "\u2705", "\uD83D\uDEAB") + '\n'
					+ Markdown.bold(tr.translate("CoreStrings", "prompt_new_value")
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
				return Mono.error(new UnexpectedReplyException(context.translate("CoreStrings", "error_invalid_input")));
			}
			return entry.setValue(value);
		}

		@Override
		public Mono<Void> visit(LongConfigEntry entry) {
			long value;
			try {
				value = Long.parseLong(input);
			} catch (NumberFormatException e) {
				return Mono.error(new UnexpectedReplyException(context.translate("CoreStrings", "error_invalid_input")));
			}
			return entry.setValue(value);
		}

		@Override
		public Mono<Void> visit(BooleanConfigEntry entry) {
			var collator = Collator.getInstance(context.getLocale());
			collator.setStrength(SECONDARY);
			var yes = context.translate("CommonStrings", "yes");
			var no = context.translate("CommonStrings", "no");
			var isYes = collator.compare(input, yes) == 0;
			var isNo = collator.compare(input, no) == 0;
			if (!isYes && !isNo) {
				return Mono.error(new UnexpectedReplyException(context.translate("CoreStrings", "error_expected_boolean")));
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
