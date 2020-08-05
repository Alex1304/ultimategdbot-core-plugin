package com.github.alex1304.ultimategdbot.core;

import static reactor.function.TupleUtils.function;

import java.time.Duration;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.util.DurationUtils;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.gateway.GatewayClient;
import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "ping",
		shortDescription = "tr:CoreStrings/ping_desc"
)
public final class PingCommand {

	@CommandAction
	@CommandDoc("tr:CoreStrings/ping_run")
	public static Mono<Void> run(Context ctx) {
		return ctx.reply(ctx.translate("CoreStrings", "pong"))
				.elapsed()
				.flatMap(function((apiLatency, message) -> message.edit(
						spec -> spec.setContent(computeLatency(ctx, ctx.event(), apiLatency)))))
				.then();
	}
	
	private static String computeLatency(Translator tr, MessageCreateEvent event, long apiLatency) {
		return tr.translate("CoreStrings", "pong") + '\n'
				+ tr.translate("CoreStrings", "api_latency") + ' ' + DurationUtils.format(Duration.ofMillis(apiLatency)) + "\n"
				+ tr.translate("CoreStrings", "gateway_latency") + ' ' + event.getClient()
						.getGatewayClient(event.getShardInfo().getIndex())
						.map(GatewayClient::getResponseTime)
						.map(DurationUtils::format)
						.orElse(tr.translate("CoreStrings", "unknown"));
	}
}
