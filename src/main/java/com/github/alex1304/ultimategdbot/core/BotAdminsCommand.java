package com.github.alex1304.ultimategdbot.core;

import static discord4j.core.retriever.EntityRetrievalStrategy.STORE_FALLBACK_REST;
import static java.util.function.Predicate.isEqual;

import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.core.database.BotAdminDao;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "botadmins",
		shortDescription = "tr:strings.core/botadmins_desc"
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
class BotAdminsCommand {

	@CommandAction
	@CommandDoc("tr:strings.core/botadmins_run")
	public Mono<Void> run(Context ctx) {
		return ctx.bot().service(DatabaseService.class)
				.withExtension(BotAdminDao.class, BotAdminDao::getAll)
				.flatMapMany(Flux::fromIterable)
				.flatMap(adminId -> ctx.bot().gateway()
						.withRetrievalStrategy(STORE_FALLBACK_REST)
						.getUserById(Snowflake.of(adminId)))
				.map(User::getTag)
				.collectSortedList(String.CASE_INSENSITIVE_ORDER)
				.map(adminList -> {
					var sb = new StringBuilder("__**" + ctx.translate("strings.core", "list") + ":**__\n\n");
					adminList.forEach(admin -> sb.append(admin).append("\n"));
					if (adminList.isEmpty()) {
						sb.append("*(" + ctx.translate("strings.core", "no_data") + ")*\n");
					}
					return sb.toString().substring(0, Math.min(sb.toString().length(), 800));
				})
				.flatMap(ctx::reply)
				.then();
	}
	
	@CommandAction("grant")
	@CommandDoc("tr:strings.core/botadmins_run_grant")
	public Mono<Void> runGrant(Context ctx, User user) {
		return ctx.bot().service(DatabaseService.class)
				.withExtension(BotAdminDao.class, dao -> dao.insertIfNotExists(user.getId().asLong()))
				.filter(isEqual(true))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings.core", "error_already_admin"))))
				.then(ctx.reply(ctx.translate("strings.core", "admin_grant_success", user.getTag()))
						.and(ctx.bot().log(ctx.bot().translate("strings.core", "admin_grant_log") + ": **" 
								+ user.getTag() + "** (" + user.getId().asString() + ")")));
	}
	
	@CommandAction("revoke")
	@CommandDoc("tr:strings.core/botadmins_run_revoke")
	public Mono<Void> runRevoke(Context ctx, User user) {
		return ctx.bot().service(DatabaseService.class)
				.withExtension(BotAdminDao.class, dao -> dao.delete(user.getId().asLong()))
				.filter(isEqual(true))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings.core", "error_already_not_admin"))))
				.then(ctx.reply(ctx.translate("strings.core", "admin_revoke_success", user.getTag()))
						.and(ctx.bot().log(ctx.bot().translate("strings.core", "admin_revoke_log") + ": **" 
								+ user.getTag() + "** (" + user.getId().asString() + ")")));
	}
}
