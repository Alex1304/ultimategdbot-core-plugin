package com.github.alex1304.ultimategdbot.core;

import static discord4j.core.retriever.EntityRetrievalStrategy.STORE_FALLBACK_REST;
import static java.util.function.Predicate.isEqual;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.core.database.BotAdminDao;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "botadmins",
		shortDescription = "tr:CoreStrings/botadmins_desc"
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
public class BotAdminsCommand extends CoreCommand {

	@CommandAction
	@CommandDoc("tr:CoreStrings/botadmins_run")
	public Mono<Void> run(Context ctx) {
		return core.getDatabaseService()
				.withExtension(BotAdminDao.class, BotAdminDao::getAll)
				.flatMapMany(Flux::fromIterable)
				.flatMap(adminId -> ctx.event().getClient()
						.withRetrievalStrategy(STORE_FALLBACK_REST)
						.getUserById(Snowflake.of(adminId)))
				.map(User::getTag)
				.collectSortedList(String.CASE_INSENSITIVE_ORDER)
				.map(adminList -> {
					var sb = new StringBuilder("__**" + ctx.translate("CoreStrings", "list") + ":**__\n\n");
					adminList.forEach(admin -> sb.append(admin).append("\n"));
					if (adminList.isEmpty()) {
						sb.append("*(" + ctx.translate("CoreStrings", "no_data") + ")*\n");
					}
					return sb.toString().substring(0, Math.min(sb.toString().length(), 800));
				})
				.flatMap(ctx::reply)
				.then();
	}
	
	@CommandAction("grant")
	@CommandDoc("tr:CoreStrings/botadmins_run_grant")
	public Mono<Void> runGrant(Context ctx, User user) {
		return core.getDatabaseService()
				.withExtension(BotAdminDao.class, dao -> dao.insertIfNotExists(user.getId().asLong()))
				.filter(isEqual(true))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("CoreStrings", "error_already_admin"))))
				.then(ctx.reply(ctx.translate("CoreStrings", "admin_grant_success", user.getTag()))
						.and(core.getLoggingService().log(Translator.to(core.getLocalizationService().getDefaultLocale())
								.translate("CoreStrings", "admin_grant_log") + ": **" 
										+ user.getTag() + "** (" + user.getId().asString() + ")")));
	}
	
	@CommandAction("revoke")
	@CommandDoc("tr:CoreStrings/botadmins_run_revoke")
	public Mono<Void> runRevoke(Context ctx, User user) {
		return core.getDatabaseService()
				.withExtension(BotAdminDao.class, dao -> dao.delete(user.getId().asLong()))
				.filter(isEqual(true))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("CoreStrings", "error_already_not_admin"))))
				.then(ctx.reply(ctx.translate("CoreStrings", "admin_revoke_success", user.getTag()))
						.and(core.getLoggingService().log(Translator.to(core.getLocalizationService().getDefaultLocale())
								.translate("CoreStrings", "admin_revoke_log") + ": **" 
										+ user.getTag() + "** (" + user.getId().asString() + ")")));
	}
}
