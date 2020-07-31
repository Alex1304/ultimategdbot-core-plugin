package com.github.alex1304.ultimategdbot.core;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

import org.jdbi.v3.core.mapper.immutables.JdbiImmutables;

import com.github.alex1304.ultimategdbot.api.PluginMetadata;
import com.github.alex1304.ultimategdbot.api.command.CommandProvider;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.command.PermissionChecker;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.localization.LocalizationService;
import com.github.alex1304.ultimategdbot.api.logging.LoggingService;
import com.github.alex1304.ultimategdbot.api.metadata.PluginMetadataService;
import com.github.alex1304.ultimategdbot.core.database.BlacklistedIdDao;
import com.github.alex1304.ultimategdbot.core.database.BotAdminDao;
import com.github.alex1304.ultimategdbot.core.database.CoreConfigDao;
import com.github.alex1304.ultimategdbot.core.database.CoreConfigData;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CoreService {

	private final CommandService commandService;
	private final DatabaseService databaseService;
	private final InteractiveMenuService interactiveMenuService;
	private final LocalizationService localizationService;
	private final LoggingService loggingService;
	
	private final String aboutText;
	private final Mono<User> botOwner;
	private final Set<PluginMetadata> pluginMetadata;

	public static Mono<CoreService> create(
			GatewayDiscordClient gateway,
			CommandService commandService,
			DatabaseService databaseService,
			InteractiveMenuService interactiveMenuService,
			LocalizationService localizationService,
			LoggingService loggingService,
			PluginMetadataService pluginMetadataService) {
		databaseService.configureJdbi(jdbi -> {
			jdbi.getConfig(JdbiImmutables.class).registerImmutable(CoreConfigData.class);
		});
		databaseService.addGuildConfigurator(CoreConfigDao.class,
				(data, tr) -> CoreConfigData.configurator(data, tr, gateway, commandService, localizationService));
		var botOwner = gateway.getApplicationInfo().flatMap(ApplicationInfo::getOwner).cache();
		return Mono.when(initBlacklist(commandService, databaseService),
						initPrefixes(commandService, databaseService),
						initLocales(databaseService, localizationService),
						initMemoryStats())
				.then(Mono.defer(CoreService::readAboutText))
				.flatMap(aboutText -> {
					var coreService = new CoreService(commandService, databaseService, interactiveMenuService,
						localizationService, loggingService, botOwner, pluginMetadataService.getPluginMetadataSet(), aboutText);
					var cmdProvider = initCommandProvider(botOwner, databaseService);
					commandService.addProvider(cmdProvider);
					return cmdProvider.addAllFromModule(CoreService.class.getModule(), null)
							.cast(CoreCommand.class)
							.doOnNext(cmd -> cmd.setCoreService(coreService))
							.then()
							.thenReturn(coreService);
				});
	}
	
	private CoreService(
			CommandService commandService,
			DatabaseService databaseService,
			InteractiveMenuService interactiveMenuService,
			LocalizationService localizationService,
			LoggingService loggingService,
			Mono<User> botOwner,
			Set<PluginMetadata> pluginMetadata,
			String aboutText) {
		this.commandService = commandService;
		this.databaseService = databaseService;
		this.interactiveMenuService = interactiveMenuService;
		this.localizationService = localizationService;
		this.loggingService = loggingService;
		this.aboutText = aboutText;
		this.botOwner = botOwner;
		this.pluginMetadata = pluginMetadata;
	}

	public CommandService getCommandService() {
		return commandService;
	}

	public DatabaseService getDatabaseService() {
		return databaseService;
	}

	public InteractiveMenuService getInteractiveMenuService() {
		return interactiveMenuService;
	}

	public LocalizationService getLocalizationService() {
		return localizationService;
	}
	
	public LoggingService getLoggingService() {
		return loggingService;
	}

	public String getAboutText() {
		return aboutText;
	}

	public Mono<User> getBotOwner() {
		return botOwner;
	}

	public Set<PluginMetadata> getPluginMetadata() {
		return pluginMetadata;
	}

	private static Mono<String> readAboutText() {
		var url = ClassLoader.getSystemResource("about.txt");
		if (url == null) {
			return Mono.just(":warning: Could not retrieve about information.");
		}
		return Mono.fromCallable(() -> String.join("\n", Files.readAllLines(Paths.get(url.toURI()))))
				.subscribeOn(Schedulers.boundedElastic());
	}
	
	private static CommandProvider initCommandProvider(Mono<User> botOwner, DatabaseService databaseService) {
		var cmdProvider = new CommandProvider(CorePlugin.PLUGIN_NAME);
		var permissionChecker = new PermissionChecker();
		permissionChecker.register(PermissionLevel.BOT_OWNER, ctx -> botOwner.map(ctx.author()::equals));
		permissionChecker.register(PermissionLevel.BOT_ADMIN, ctx -> databaseService
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
	
	private static Mono<Void> initBlacklist(CommandService commandService, DatabaseService databaseService) {
		return databaseService.withExtension(BlacklistedIdDao.class, BlacklistedIdDao::getAll)
				.flatMapMany(Flux::fromIterable)
				.doOnNext(commandService::blacklist)
				.then();
	}
	
	private static Mono<Void> initPrefixes(CommandService commandService, DatabaseService databaseService) {
		var defaultPrefix = commandService.getCommandPrefix();
		return databaseService
				.withExtension(CoreConfigDao.class, dao -> dao.getAllNonDefaultPrefixes(defaultPrefix))
				.flatMapMany(Flux::fromIterable)
				.doOnNext(data -> commandService.setPrefixForGuild(data.guildId().asLong(), data.prefix().orElseThrow()))
				.then();
	}
	
	private static Mono<Void> initLocales(DatabaseService databaseService, LocalizationService localizationService) {
		return databaseService
				.withExtension(CoreConfigDao.class, dao -> dao.getAllNonDefaultLocales(
						localizationService.getDefaultLocale().toLanguageTag()))
				.flatMapMany(Flux::fromIterable)
				.doOnNext(data -> localizationService.setLocaleForGuild(data.guildId().asLong(),
						Locale.forLanguageTag(data.locale().orElseThrow())))
				.then();
	}
	
	private static Mono<Void> initMemoryStats() {
		return Mono.fromRunnable(MemoryStats::start);
	}
}
