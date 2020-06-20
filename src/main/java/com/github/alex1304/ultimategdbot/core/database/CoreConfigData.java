package com.github.alex1304.ultimategdbot.core.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalValue;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.StringConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.Validator;
import com.github.alex1304.ultimategdbot.api.localization.LocalizationService;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.Channel;

@Value.Immutable
public interface CoreConfigData extends GuildConfigData<CoreConfigData> {
	
	Optional<String> prefix();
	
	Optional<Snowflake> channelChangelogId();
	
	Optional<String> locale();

	@Override
	default GuildConfigurator<CoreConfigData> configurator(Translator tr, Bot bot) {
		return GuildConfigurator.builder(tr.translate("strings_core", "core_guildconfig_title"), this, CoreConfigDao.class)
				.setDescription(tr.translate("strings_core", "core_guildconfig_desc"))
				.addEntry(StringConfigEntry.<CoreConfigData>builder("prefix")
						.setValueGetter(forOptionalValue(CoreConfigData::prefix))
						.setValueSetter((data, value) -> ImmutableCoreConfigData.builder()
								.from(data)
								.prefix(Optional.ofNullable(value))
								.build())
						.setValidator(Validator.denyingIf(String::isBlank, tr.translate("strings_core", "validate_not_blank"))))
				.addEntry(GuildChannelConfigEntry.<CoreConfigData>builder("channel_changelog")
						.setDisplayName(tr.translate("strings_core", "display_channel_changelog"))
						.setValueGetter(forOptionalGuildChannel(bot, CoreConfigData::channelChangelogId))
						.setValueSetter((data, channel) -> ImmutableCoreConfigData.builder()
								.from(data)
								.channelChangelogId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(StringConfigEntry.<CoreConfigData>builder("locale")
						.setDisplayName("language")
						.setDescription(tr.translate("strings_core", "desc_locale") + '\n' + displayLocaleList(bot))
						.setValueGetter(forOptionalValue(CoreConfigData::locale))
						.setValueSetter((data, value) -> ImmutableCoreConfigData.builder()
								.from(data)
								.locale(Optional.ofNullable(value))
								.build())
						.setValidator(Validator.allowingIf(value -> isLocaleSupported(bot, value),
								tr.translate("strings_core", "unrecognized_locale"))))
				.onSave(data -> bot.service(CommandService.class)
						.setPrefixForGuild(data.guildId().asLong(), data.prefix().orElse(null)))
				.build();
	}
	
	private static boolean isLocaleSupported(Bot bot, String value) {
		return bot.hasService(LocalizationService.class)
				? bot.service(LocalizationService.class).getSupportedLocales()
						.stream()
						.map(Locale::toLanguageTag)
						.filter(tag -> tag.equals(value))
						.findAny()
						.isPresent()
				: bot.getLocale().toLanguageTag().equals(value);
	}
	
	private static String displayLocaleList(Bot bot) {
		var locales = bot.hasService(LocalizationService.class)
				? bot.service(LocalizationService.class).getSupportedLocales()
				: Collections.singleton(bot.getLocale());
		return locales.stream()
				.map(locale -> "- `" + locale.toLanguageTag() + "` [" + locale.getDisplayName(locale) + "]")
				.sorted()
				.collect(Collectors.joining("\n"));
	}
}
