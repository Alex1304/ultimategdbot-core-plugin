package com.github.alex1304.ultimategdbot.core;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

import org.jdbi.v3.core.mapper.immutables.JdbiImmutables;

import com.github.alex1304.ultimategdbot.api.command.CommandProvider;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.service.BotService;
import com.github.alex1304.ultimategdbot.api.service.RootServiceSetupHelper;
import com.github.alex1304.ultimategdbot.core.database.BlacklistedIdDao;
import com.github.alex1304.ultimategdbot.core.database.BotAdminDao;
import com.github.alex1304.ultimategdbot.core.database.CoreConfigDao;
import com.github.alex1304.ultimategdbot.core.database.CoreConfigData;

import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class CoreService {

	private final BotService bot;
	private final String aboutText;
	private final Mono<User> botOwner;

	public static Mono<CoreService> create(BotService bot, String aboutText) {
		bot.database().configureJdbi(jdbi -> {
			jdbi.getConfig(JdbiImmutables.class).registerImmutable(CoreConfigData.class);
		});
		bot.database().addGuildConfigurator(CoreConfigDao.class,
				(data, tr) -> CoreConfigData.configurator(data, tr, bot));
		var botOwner = bot.gateway().getApplicationInfo().flatMap(ApplicationInfo::getOwner).cache();
		return RootServiceSetupHelper.create(() -> new CoreService(bot, botOwner, aboutText))
				.setSetupSequence(Mono.when(
						initBlacklist(bot),
						initPrefixes(bot),
						initLocales(bot),
						initMemoryStats()))
				.addCommandProvider(bot.command(), initCommandProvider(botOwner, bot.database(), bot.command()))
				.setup();
	}

	private CoreService(BotService bot, Mono<User> botOwner, String aboutText) {
		this.bot = bot;
		this.aboutText = aboutText;
		this.botOwner = botOwner;
	}
	
	public BotService bot() {
		return bot;
	}

	public String aboutText() {
		return aboutText;
	}

	public Mono<User> botOwner() {
		return botOwner;
	}

	public static Mono<String> readAboutText() {
		var url = ClassLoader.getSystemResource("about.txt");
		if (url == null) {
			return Mono.just(":warning: Could not retrieve about information.");
		}
		return Mono.fromCallable(() -> String.join("\n", Files.readAllLines(Paths.get(url.toURI()))))
				.subscribeOn(Schedulers.boundedElastic());
	}
	
	private static CommandProvider initCommandProvider(Mono<User> botOwner, DatabaseService databaseService, CommandService commandService) {
		var cmdProvider = new CommandProvider(CorePlugin.PLUGIN_NAME, commandService.getPermissionChecker());
		var permissionChecker = commandService.getPermissionChecker();
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
		return cmdProvider;
	}
	
	private static Mono<Void> initBlacklist(BotService bot) {
		return bot.database().withExtension(BlacklistedIdDao.class, BlacklistedIdDao::getAll)
				.flatMapMany(Flux::fromIterable)
				.doOnNext(bot.command()::blacklist)
				.then();
	}
	
	private static Mono<Void> initPrefixes(BotService bot) {
		var defaultPrefix = bot.command().getCommandPrefix();
		return bot.database()
				.withExtension(CoreConfigDao.class, dao -> dao.getAllNonDefaultPrefixes(defaultPrefix))
				.flatMapMany(Flux::fromIterable)
				.doOnNext(data -> bot.command().setPrefixForGuild(data.guildId().asLong(), data.prefix().orElseThrow()))
				.then();
	}
	
	private static Mono<Void> initLocales(BotService bot) {
		return bot.database()
				.withExtension(CoreConfigDao.class, dao -> dao.getAllNonDefaultLocales(
						bot.localization().getLocale().toLanguageTag()))
				.flatMapMany(Flux::fromIterable)
				.doOnNext(data -> bot.localization().setLocaleForGuild(data.guildId().asLong(),
						Locale.forLanguageTag(data.locale().orElseThrow())))
				.then();
	}
	
	private static Mono<Void> initMemoryStats() {
		return Mono.fromRunnable(MemoryStats::start);
	}
}
