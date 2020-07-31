package com.github.alex1304.ultimategdbot.core.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalValue;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.StringConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.Validator;
import com.github.alex1304.ultimategdbot.api.localization.LocalizationService;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.Channel;

@Value.Immutable
public interface CoreConfigData extends GuildConfigData<CoreConfigData> {
	
	Optional<String> prefix();
	
	Optional<Snowflake> channelChangelogId();
	
	Optional<String> locale();

	static GuildConfigurator<CoreConfigData> configurator(
			CoreConfigData configData,
			Translator tr,
			GatewayDiscordClient gateway,
			CommandService commandService,
			LocalizationService localizationService) {
		return GuildConfigurator.builder(tr.translate("CoreStrings", "core_guildconfig_title"), configData, CoreConfigDao.class)
				.setDescription(tr.translate("CoreStrings", "core_guildconfig_desc"))
				.addEntry(StringConfigEntry.<CoreConfigData>builder("prefix")
						.setValueGetter(forOptionalValue(CoreConfigData::prefix))
						.setValueSetter((data, value) -> ImmutableCoreConfigData.builder()
								.from(data)
								.prefix(Optional.ofNullable(value))
								.build())
						.setValidator(Validator.denyingIf(String::isBlank, tr.translate("CoreStrings", "validate_not_blank"))))
				.addEntry(GuildChannelConfigEntry.<CoreConfigData>builder("channel_changelog")
						.setDisplayName(tr.translate("CoreStrings", "display_channel_changelog"))
						.setValueGetter(forOptionalGuildChannel(gateway, CoreConfigData::channelChangelogId))
						.setValueSetter((data, channel) -> ImmutableCoreConfigData.builder()
								.from(data)
								.channelChangelogId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(StringConfigEntry.<CoreConfigData>builder("locale")
						.setDisplayName("language")
						.setDescription(tr.translate("CoreStrings", "desc_locale") + '\n' + displayLocaleList(localizationService))
						.setValueGetter(forOptionalValue(CoreConfigData::locale))
						.setValueSetter((data, value) -> ImmutableCoreConfigData.builder()
								.from(data)
								.locale(Optional.ofNullable(value))
								.build())
						.setValidator(Validator.allowingIf(value -> isLocaleSupported(value, localizationService),
								tr.translate("CoreStrings", "unrecognized_locale"))))
				.onSave(data -> {
					commandService.setPrefixForGuild(data.guildId().asLong(), data.prefix().orElse(null));
					localizationService.setLocaleForGuild(data.guildId().asLong(),
									data.locale().map(Locale::forLanguageTag).orElse(null));
				})
				.build();
	}
	
	private static boolean isLocaleSupported(String value, LocalizationService localizationService) {
		return localizationService.getSupportedLocales()
						.stream()
						.map(Locale::toLanguageTag)
						.filter(tag -> tag.equals(value))
						.findAny()
						.isPresent();
	}
	
	private static String displayLocaleList(LocalizationService localizationService) {
		return localizationService.getSupportedLocales().stream()
				.map(locale -> "- `" + locale.toLanguageTag() + "` [" + locale.getDisplayName(locale) + "]")
				.sorted()
				.collect(Collectors.joining("\n"));
	}
}
