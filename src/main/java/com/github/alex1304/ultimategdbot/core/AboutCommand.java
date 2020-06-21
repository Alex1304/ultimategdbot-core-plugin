package com.github.alex1304.ultimategdbot.core;

import static com.github.alex1304.ultimategdbot.api.util.VersionUtils.API_GIT_RESOURCE;
import static java.util.Objects.requireNonNull;
import static reactor.function.TupleUtils.function;

import java.util.HashMap;
import java.util.Properties;
import java.util.stream.Collectors;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;
import com.github.alex1304.ultimategdbot.api.util.PropertyReader;
import com.github.alex1304.ultimategdbot.api.util.VersionUtils;

import discord4j.common.GitProperties;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@CommandDescriptor(
		aliases = "about",
		shortDescription = "tr:CoreStrings/about_desc"
)
class AboutCommand {

	private static final Mono<Properties> D4J_PROPS = Mono.fromCallable(GitProperties::getProperties).cache();

	private final String aboutText;
	
	public AboutCommand(String aboutText) {
		this.aboutText = requireNonNull(aboutText);
	}

	@CommandAction
	@CommandDoc("tr:CoreStrings/about_run")
	public Mono<Void> run(Context ctx) {
		return Mono.zip(D4J_PROPS.map(PropertyReader::fromProperties).transform(props -> version(ctx, props)),
						VersionUtils.getGitProperties(API_GIT_RESOURCE).transform(props -> version(ctx, props)),
						ctx.bot().owner(),
						ctx.bot().gateway().getSelf(),
						ctx.bot().gateway().getGuilds().count(),
						ctx.bot().gateway().getUsers().count())
				.flatMap(function((d4jVersion, apiVersion, botOwner, self, guildCount, userCount) -> {
					var versionInfoBuilder = new StringBuilder("**")
							.append(ctx.translate("CoreStrings", "ugdb_api_version"))
							.append("** ");
					versionInfoBuilder.append(apiVersion).append("\n");
					versionInfoBuilder.append("**");
					versionInfoBuilder.append(ctx.translate("CoreStrings", "d4j_version"));
					versionInfoBuilder.append("** ")
							.append(d4jVersion)
							.append("\n");
//					pluginVersions.forEach((k, v) -> {
//						versionInfoBuilder.append("**")
//							.append(k)
//							.append(" plugin version:** ")
//							.append(v)
//							.append("\n");
//					});
					for (var pluginMetadata : ctx.bot().plugins()) {
						versionInfoBuilder.append("**")
								.append(pluginMetadata.getName())
								.append(' ')
								.append(ctx.translate("CoreStrings", "plugin"))
								.append("**\n")
								.append("> **")
								.append(ctx.translate("CoreStrings", "version"))
								.append("** ")
								.append(pluginMetadata.getVersion().orElse("*" + ctx.translate("CoreStrings", "unknown") + "*"))
								.append(pluginMetadata.getDescription()
										.map(s -> "\n> **" + ctx.translate("CoreStrings", "description") + "** " + s)
										.orElse(""));
						if (!pluginMetadata.getDevelopers().isEmpty()) {
							versionInfoBuilder.append(pluginMetadata.getDevelopers().stream()
									.collect(Collectors.joining(", ", "\n> **" + ctx.translate("CoreStrings", "developers") + "** ", "")));
						}
						versionInfoBuilder
								.append(pluginMetadata.getUrl()
										.map(s -> "\n> **" + ctx.translate("CoreStrings", "url") + "** <" + s + ">")
										.orElse(""))
								.append('\n');
					}
					// Remove last \n
					if (!ctx.bot().plugins().isEmpty()) {
						versionInfoBuilder.deleteCharAt(versionInfoBuilder.length() - 1);
					}
					var vars = new HashMap<String, String>();
					vars.put("bot_name", self.getUsername());
					vars.put("bot_owner", botOwner.getTag());
					vars.put("server_count", "" + guildCount);
					vars.put("user_count", "" + userCount);
					vars.put("version_info", versionInfoBuilder.toString());
					var box = new Object() { private String text = aboutText; };
					vars.forEach((k, v) -> box.text = box.text.replaceAll("\\{\\{ *" + k + " *\\}\\}", "" + v));
					return ctx.bot().service(InteractiveMenuService.class)
							.createPaginated(box.text, 1990)
							.open(ctx);
				}))
				.subscribeOn(Schedulers.boundedElastic())
				.then();
	}
	
	private static Mono<String> version(Translator tr, Mono<PropertyReader> props) {
		return props.map(p -> p.readOptional(GitProperties.APPLICATION_VERSION)
						.orElse("*" + tr.translate("CoreStrings", "unknown") + "*"))
				.defaultIfEmpty("*" + tr.translate("CoreStrings", "unknown") + "*");
	}
}
