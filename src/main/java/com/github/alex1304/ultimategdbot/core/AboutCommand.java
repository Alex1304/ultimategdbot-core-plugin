package com.github.alex1304.ultimategdbot.core;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static reactor.function.TupleUtils.function;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Properties;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;

import discord4j.common.GitProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@CommandDescriptor(
		aliases = "about",
		shortDescription = "Shows information about the bot itself."
)
class AboutCommand {

	private static final Mono<Properties> D4J_PROPS = Mono.fromCallable(GitProperties::getProperties).cache();

	private final String aboutText;
	
	public AboutCommand(String aboutText) {
		this.aboutText = requireNonNull(aboutText);
	}

	@CommandAction
	@CommandDoc("Displays a custom text containing various information about the bot, such as the number "
			+ "of servers the bot is in, the link to add it to your server, the link to the official "
			+ "support server, the version of plugins it uses, and credits to people who contributed to "
			+ "its development.")
	public Mono<Void> run(Context ctx) {
		return Mono.zip(D4J_PROPS.transform(AboutCommand::version),
						getAPIGitProperties().transform(AboutCommand::version),
						Flux.fromIterable(ctx.bot().plugins())
								.flatMap(p -> p.getGitProperties()
										.transform(AboutCommand::version)
										.map(v -> Tuples.of(p.getName(), v)))
								.sort(Comparator.comparing(Tuple2::getT1, String.CASE_INSENSITIVE_ORDER))
								.collect(toMap(Tuple2::getT1, Tuple2::getT2, (a, b) -> a, LinkedHashMap::new)),
						ctx.bot().owner(),
						ctx.bot().gateway().getSelf(),
						ctx.bot().gateway().getGuilds().count(),
						ctx.bot().gateway().getUsers().count())
				.flatMap(function((d4jVersion, apiVersion, pluginVersions, botOwner, self, guildCount, userCount) -> {
					var versionInfoBuilder = new StringBuilder("**")
							.append("UltimateGDBot API version:** ");
					versionInfoBuilder.append(apiVersion).append("\n");
					versionInfoBuilder.append("**Discord4J version:** ")
							.append(d4jVersion)
							.append("\n");
					pluginVersions.forEach((k, v) -> {
						versionInfoBuilder.append("**")
							.append(k)
							.append(" plugin version:** ")
							.append(v)
							.append("\n");
					});
					var vars = new HashMap<String, String>();
					vars.put("bot_name", self.getUsername());
					vars.put("bot_owner", botOwner.getTag());
					vars.put("server_count", "" + guildCount);
					vars.put("user_count", "" + userCount);
					vars.put("version_info", versionInfoBuilder.toString());
					var result = new String[] { aboutText };
					vars.forEach((k, v) -> result[0] = result[0].replaceAll("\\{\\{ *" + k + " *\\}\\}", String.valueOf(v)));
					return ctx.reply(result[0]);
				}))
				.subscribeOn(Schedulers.boundedElastic())
				.then();
	}
	
	private static Mono<Properties> getAPIGitProperties() {
		return Mono.fromCallable(() -> {
			var props = new Properties();
			try (var stream = ClassLoader.getSystemResourceAsStream(
					"META-INF/git/ultimategdbot.git.properties")) {
				if (stream != null) {
					props.load(stream);
				}
			}
			return props;
		}).subscribeOn(Schedulers.boundedElastic());
	}
	
	private static Mono<String> version(Mono<Properties> props) {
		return props
				.map(g -> g.getProperty(GitProperties.APPLICATION_VERSION, "*unknown*"))
				.defaultIfEmpty("*unknown*");
	}
}
