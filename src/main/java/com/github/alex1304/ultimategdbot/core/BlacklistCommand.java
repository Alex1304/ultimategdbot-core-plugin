package com.github.alex1304.ultimategdbot.core;

import static java.util.function.Predicate.isEqual;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.core.database.BlacklistedIdDao;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "blacklist",
		shortDescription = "tr:CoreStrings/blacklist_desc"
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
public class BlacklistCommand extends CoreCommand {

	@CommandAction("add")
	@CommandDoc("tr:CoreStrings/blacklist_run_add")
	public Mono<Void> runAdd(Context ctx, long id) {
		return core.getDatabaseService()
				.withExtension(BlacklistedIdDao.class, dao -> dao.insertIfNotExists(id))
				.filter(isEqual(true))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("CoreStrings", "error_already_blacklisted"))))
				.then(Mono.fromRunnable(() -> core.getCommandService().blacklist(id)))
				.then(ctx.reply(ctx.translate("CoreStrings", "blacklist_success", id))
						.and(core.getLoggingService().log(Translator.to(core.getLocalizationService().getDefaultLocale())
								.translate("CoreStrings", "blacklist_log") + ": " + id)));
	}

	@CommandAction("remove")
	@CommandDoc("tr:CoreStrings/blacklist_run_remove")
	public Mono<Void> runRemove(Context ctx, long id) {
		return core.getDatabaseService()
				.withExtension(BlacklistedIdDao.class, dao -> dao.delete(id))
				.filter(isEqual(true))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("CoreStrings", "error_already_not_blacklisted"))))
				.then(Mono.fromRunnable(() -> core.getCommandService().unblacklist(id)))
				.then(ctx.reply(ctx.translate("CoreStrings", "unblacklist_success", id))
						.and(core.getLoggingService().log(Translator.to(core.getLocalizationService().getDefaultLocale())
								.translate("CoreStrings", "unblacklist_log") + ": " + id)));
	}
}
