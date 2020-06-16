package com.github.alex1304.ultimategdbot.core;

import static java.util.function.Predicate.isEqual;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.core.database.BlacklistedIdDao;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "blacklist",
		shortDescription = "tr:cmddoc_core_blacklist/short_description"
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
class BlacklistCommand {

	@CommandAction("add")
	@CommandDoc("tr:cmddoc_core_blacklist/run_add")
	public Mono<Void> runAdd(Context ctx, long id) {
		return ctx.bot().service(DatabaseService.class)
				.withExtension(BlacklistedIdDao.class, dao -> dao.insertIfNotExists(id))
				.filter(isEqual(true))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("cmdtext_core_blacklist", "error_already_blacklisted"))))
				.then(Mono.fromRunnable(() -> ctx.bot().service(CommandService.class).blacklist(id)))
				.then(ctx.reply(ctx.translate("cmdtext_core_blacklist", "blacklist_success", id))
						.and(ctx.bot().log(Translator.to(ctx.bot().service(CommandService.class).getDefaultLocale())
								.translate("cmdtext_core_blacklist", "blacklist_log") + ": " + id)));
	}

	@CommandAction("remove")
	@CommandDoc("tr:cmddoc_core_blacklist/run_remove")
	public Mono<Void> runRemove(Context ctx, long id) {
		return ctx.bot().service(DatabaseService.class)
				.withExtension(BlacklistedIdDao.class, dao -> dao.delete(id))
				.filter(isEqual(true))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("cmdtext_core_blacklist", "error_already_not_blacklisted"))))
				.then(Mono.fromRunnable(() -> ctx.bot().service(CommandService.class).unblacklist(id)))
				.then(ctx.reply(ctx.translate("cmdtext_core_blacklist", "unblacklist_success", id))
						.and(ctx.bot().log(Translator.to(ctx.bot().service(CommandService.class).getDefaultLocale())
								.translate("cmdtext_core_blacklist", "unblacklist_log") + ": " + id)));
	}
}
