package com.github.alex1304.ultimategdbot.core.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalValue;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.StringConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.Validator;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.Channel;

@Value.Immutable
public interface CoreConfigData extends GuildConfigData<CoreConfigData> {
	
	Optional<String> prefix();
	
	Optional<Snowflake> channelChangelogId();

	@Override
	default GuildConfigurator<CoreConfigData> configurator(Translator tr, Bot bot) {
		return GuildConfigurator.builder(tr.translate("guildconfig_core", "title"), this, CoreConfigDao.class)
				.setDescription(tr.translate("guildconfig_core", "desc"))
				.addEntry(StringConfigEntry.<CoreConfigData>builder("prefix")
						.setValueGetter(forOptionalValue(CoreConfigData::prefix))
						.setValueSetter((data, value) -> ImmutableCoreConfigData.builder()
								.from(data)
								.prefix(Optional.ofNullable(value))
								.build())
						.setValidator(Validator.denyingIf(String::isBlank, tr.translate("guildconfig_core", "validate_not_blank"))))
				.addEntry(GuildChannelConfigEntry.<CoreConfigData>builder("channel_changelog")
						.setDisplayName(tr.translate("guildconfig_core", "display_channel_changelog"))
						.setValueGetter(forOptionalGuildChannel(bot, CoreConfigData::channelChangelogId))
						.setValueSetter((data, channel) -> ImmutableCoreConfigData.builder()
								.from(data)
								.channelChangelogId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.onSave(data -> bot.service(CommandService.class)
						.setPrefixForGuild(data.guildId().asLong(), data.prefix().orElse(null)))
				.build();
	}
}
