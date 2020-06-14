package com.github.alex1304.ultimategdbot.core.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalValue;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
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
	default GuildConfigurator<CoreConfigData> configurator(Bot bot) {
		return GuildConfigurator.builder("General settings", this, CoreConfigDao.class)
				.setDescription("The command prefix that the bot will respond to in this server.")
				.addEntry(StringConfigEntry.<CoreConfigData>builder("prefix")
						.setValueGetter(forOptionalValue(CoreConfigData::prefix))
						.setValueSetter((data, value) -> ImmutableCoreConfigData.builder()
								.from(data)
								.prefix(Optional.ofNullable(value))
								.build())
						.setValidator(Validator.denyingIf(String::isBlank, "cannot be blank")))
				.addEntry(GuildChannelConfigEntry.<CoreConfigData>builder("channel_changelog")
						.setDisplayName("channel for bot changelog announcements")
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
