package com.github.alex1304.ultimategdbot.core;

import static com.github.alex1304.ultimategdbot.core.CoreServices.CORE;

import java.util.List;

import com.github.alex1304.rdi.ServiceReference;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.PluginMetadata;
import com.github.alex1304.ultimategdbot.api.util.VersionUtils;

import discord4j.common.GitProperties;
import reactor.core.publisher.Mono;

public final class CorePlugin implements Plugin {
	
	public static final String PLUGIN_NAME = "Core";

	@Override
	public ServiceReference<?> rootService() {
		return CORE;
	}
	
	@Override
	public Mono<PluginMetadata> metadata() {
		return VersionUtils.getGitProperties("META-INF/git/core.git.properties")
				.map(props -> props.readOptional(GitProperties.APPLICATION_VERSION))
				.map(version -> PluginMetadata.builder(PLUGIN_NAME)
						.setDescription("Essential commands for a basic Discord bot.")
						.setVersion(version.orElse(null))
						.setDevelopers(List.of("Alex1304"))
						.setUrl("https://github.com/ultimategdbot/ultimategdbot-core-plugin")
						.build());
	}
}
