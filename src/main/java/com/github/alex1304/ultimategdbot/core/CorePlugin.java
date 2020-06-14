package com.github.alex1304.ultimategdbot.core;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.jdbi.v3.core.mapper.immutables.JdbiImmutables;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.PluginMetadata;
import com.github.alex1304.ultimategdbot.api.command.CommandProvider;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.command.PermissionChecker;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.service.Service;
import com.github.alex1304.ultimategdbot.api.util.VersionUtils;
import com.github.alex1304.ultimategdbot.core.database.BlacklistedIdDao;
import com.github.alex1304.ultimategdbot.core.database.BotAdminDao;
import com.github.alex1304.ultimategdbot.core.database.CoreConfigDao;
import com.github.alex1304.ultimategdbot.core.database.CoreConfigData;

import discord4j.common.GitProperties;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CorePlugin implements Plugin {
	
	private static final String PLUGIN_NAME = "Core";

	@Override
	public Mono<Void> setup(Bot bot) {
		var db = bot.service(DatabaseService.class);
		db.configureJdbi(jdbi -> {
			jdbi.getConfig(JdbiImmutables.class).registerImmutable(CoreConfigData.class);
		});
		db.registerGuildConfigExtension(CoreConfigDao.class);
		return readAboutText()
				.map(CorePlugin::initCommandProvider)
				.doOnNext(bot.service(CommandService.class)::addProvider)
				.and(initBlacklist(bot))
				.and(initPrefixes(bot))
				.and(initMemoryStats());
	}

	@Override
	public Mono<PluginMetadata> metadata() {
		return VersionUtils.getGitProperties("META-INF/git/core.git.properties")
				.map(props -> props.readOptional(GitProperties.APPLICATION_VERSION))
				.map(version -> PluginMetadata.builder(PLUGIN_NAME)
					.setDescription("Essential commands for a basic Discord bot")
					.setVersion(version.orElse(null))
					.setDevelopers(List.of("Alex1304"))
					.setUrl("https://github.com/ultimategdbot/ultimategdbot-core-plugin")
					.build());
	}
	
	@Override
	public Set<Class<? extends Service>> requiredServices() {
		return Set.of(CommandService.class, DatabaseService.class, InteractiveMenuService.class);
	}
	
	private static Mono<String> readAboutText() {
		var url = ClassLoader.getSystemResource("about.txt");
		if (url == null) {
			return Mono.just(":warning: Could not retrieve about information.");
		}
		return Mono.fromCallable(() -> String.join("\n", Files.readAllLines(Paths.get(url.toURI()))))
				.subscribeOn(Schedulers.boundedElastic());
	}
	
	private static CommandProvider initCommandProvider(String aboutText) {
		// Register commands
		var cmdProvider = new CommandProvider(PLUGIN_NAME);
		cmdProvider.addAnnotated(new ChangelogCommand());
		cmdProvider.addAnnotated(new HelpCommand());
		cmdProvider.addAnnotated(new PingCommand());
		cmdProvider.addAnnotated(new SetupCommand());
		cmdProvider.addAnnotated(new LogoutCommand());
		cmdProvider.addAnnotated(new AboutCommand(aboutText));
		cmdProvider.addAnnotated(new BotAdminsCommand());
		cmdProvider.addAnnotated(new BlacklistCommand());
		cmdProvider.addAnnotated(new RuntimeCommand());
		// Register permissions
		var permissionChecker = new PermissionChecker();
		permissionChecker.register(PermissionLevel.BOT_OWNER, ctx -> ctx.bot().owner()
				.map(ctx.author()::equals));
		permissionChecker.register(PermissionLevel.BOT_ADMIN, ctx -> ctx.bot().service(DatabaseService.class)
				.withExtension(BotAdminDao.class, dao -> dao.get(ctx.author().getId().asLong()))
				.flatMap(Mono::justOrEmpty)
				.hasElement());
		permissionChecker.register(PermissionLevel.GUILD_OWNER, ctx -> ctx.event().getGuild()
				.map(Guild::getOwnerId)
				.map(ctx.author().getId()::equals));
		permissionChecker.register(PermissionLevel.GUILD_ADMIN, ctx -> ctx.event().getMessage().getChannel()
				.ofType(GuildChannel.class)
				.flatMap(c -> c.getEffectivePermissions(ctx.author().getId())
				.map(ps -> ps.contains(Permission.ADMINISTRATOR))));
		cmdProvider.setPermissionChecker(permissionChecker);
		return cmdProvider;
	}
	
	private static Mono<Void> initBlacklist(Bot bot) {
		return bot.service(DatabaseService.class).withExtension(BlacklistedIdDao.class, BlacklistedIdDao::getAll)
				.flatMapMany(Flux::fromIterable)
				.doOnNext(bot.service(CommandService.class)::blacklist)
				.then();
	}
	
	private static Mono<Void> initPrefixes(Bot bot) {
		var commandService = bot.service(CommandService.class);
		var defaultPrefix = commandService.getCommandPrefix();
		return bot.service(DatabaseService.class)
				.withExtension(CoreConfigDao.class, dao -> dao.getAllNonDefaultPrefixes(defaultPrefix))
				.flatMapMany(Flux::fromIterable)
				.doOnNext(data -> commandService.setPrefixForGuild(data.guildId().asLong(), data.prefix().orElseThrow()))
				.then();
	}
	
	private static Mono<Void> initMemoryStats() {
		return Mono.fromRunnable(MemoryStats::start);
	}
}
