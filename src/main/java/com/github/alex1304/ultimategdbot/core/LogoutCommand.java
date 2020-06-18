package com.github.alex1304.ultimategdbot.core;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "logout",
		shortDescription = "tr:strings_core/logout_desc"
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
class LogoutCommand {
	
	@CommandAction
	public Mono<Void> run(Context ctx) {
		return ctx.reply(ctx.translate("strings_core", "disconnecting"))
				.then(ctx.bot().gateway().logout());
	}
}
