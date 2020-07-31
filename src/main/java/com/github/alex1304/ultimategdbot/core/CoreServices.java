package com.github.alex1304.ultimategdbot.core;

import static com.github.alex1304.rdi.config.FactoryMethod.staticFactory;
import static com.github.alex1304.rdi.config.Injectable.ref;
import static com.github.alex1304.ultimategdbot.api.CommonServices.COMMAND_SERVICE;
import static com.github.alex1304.ultimategdbot.api.CommonServices.DATABASE_SERVICE;
import static com.github.alex1304.ultimategdbot.api.CommonServices.DISCORD_GATEWAY_CLIENT;
import static com.github.alex1304.ultimategdbot.api.CommonServices.INTERACTIVE_MENU_SERVICE;
import static com.github.alex1304.ultimategdbot.api.CommonServices.LOCALIZATION_SERVICE;
import static com.github.alex1304.ultimategdbot.api.CommonServices.LOGGING_SERVICE;
import static com.github.alex1304.ultimategdbot.api.CommonServices.PLUGIN_METADATA_SERVICE;

import java.util.Set;

import com.github.alex1304.rdi.ServiceReference;
import com.github.alex1304.rdi.config.ServiceDescriptor;
import com.github.alex1304.ultimategdbot.api.BotConfig;
import com.github.alex1304.ultimategdbot.api.ServiceDeclarator;

import reactor.core.publisher.Mono;

public class CoreServices implements ServiceDeclarator {
	
	public static final ServiceReference<CoreService> CORE_SERVICE = ServiceReference.ofType(CoreService.class);

	@Override
	public Set<ServiceDescriptor> declareServices(BotConfig botConfig) {
		return Set.of(
				ServiceDescriptor.builder(CORE_SERVICE)
						.setFactoryMethod(staticFactory("create", Mono.class,
								ref(DISCORD_GATEWAY_CLIENT),
								ref(COMMAND_SERVICE),
								ref(DATABASE_SERVICE),
								ref(INTERACTIVE_MENU_SERVICE),
								ref(LOCALIZATION_SERVICE),
								ref(LOGGING_SERVICE),
								ref(PLUGIN_METADATA_SERVICE)))
						.build()
		);
	}

}
