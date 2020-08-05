package com.github.alex1304.ultimategdbot.core;

import static com.github.alex1304.rdi.config.FactoryMethod.*;
import static com.github.alex1304.rdi.config.Injectable.ref;
import static com.github.alex1304.ultimategdbot.api.service.CommonServices.BOT;

import java.util.Set;

import com.github.alex1304.rdi.ServiceReference;
import com.github.alex1304.rdi.config.ServiceDescriptor;
import com.github.alex1304.ultimategdbot.api.BotConfig;
import com.github.alex1304.ultimategdbot.api.service.ServiceDeclarator;

import reactor.core.publisher.Mono;

public final class CoreServices implements ServiceDeclarator {
	
	public static final ServiceReference<CoreService> CORE = ServiceReference.ofType(CoreService.class);
	public static final ServiceReference<String> ABOUT_TEXT = ServiceReference.of("core.aboutText", String.class);

	@Override
	public Set<ServiceDescriptor> declareServices(BotConfig botConfig) {
		return Set.of(
				ServiceDescriptor.builder(CORE)
						.setFactoryMethod(staticFactory("create", Mono.class,
								ref(BOT),
								ref(ABOUT_TEXT)))
						.build(),
				ServiceDescriptor.builder(ABOUT_TEXT)
						.setFactoryMethod(externalStaticFactory(CoreService.class, "readAboutText", Mono.class))
						.build()
		);
	}

}
