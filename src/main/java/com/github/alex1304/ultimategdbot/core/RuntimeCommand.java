package com.github.alex1304.ultimategdbot.core;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.util.DurationUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "runtime",
		shortDescription = "tr:cmddoc_core_runtime/short_description"
)
class RuntimeCommand {

	@CommandAction
	@CommandDoc("tr:cmddoc_core_runtime/run")
	public Mono<Void> run(Context ctx) {
		return ctx.channel().typeUntil(
				Mono.zip(objArray -> Flux.fromArray(objArray).cast(EmbedField.class).collectList(),
						uptime(ctx),
						memory(ctx),
						shardInfo(ctx),
						cacheInfo(ctx))
				.flatMap(Function.identity())
				.flatMap(embedFields -> ctx.reply(spec -> spec.setEmbed(embed -> {
					embedFields.forEach(field -> embed.addField(field.title, field.content, false));
					embed.setTimestamp(Instant.now());
				}))))
				.then();
	}

	private static Mono<EmbedField> uptime(Translator tr) {
		return Mono.just(new EmbedField(tr.translate("cmdtext_core_runtime", "uptime"),
				tr.translate("cmdtext_core_runtime", "uptime_value", DurationUtils.format(
						Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime()).withNanos(0)))));
	}
	
	private static Mono<EmbedField> memory(Context ctx) {
		return MemoryStats.getStats()
				.map(memStats -> {
					var total = memStats.totalMemory;
					var max = memStats.maxMemory;
					var used = memStats.usedMemory;
					var sb = new StringBuilder();
					sb.append(ctx.translate("cmdtext_core_runtime", "max_ram")).append(' ').append(SystemUnit.format(max)).append("\n");
					sb.append(ctx.translate("cmdtext_core_runtime", "jvm_size")).append(' ').append(SystemUnit.format(total))
							.append(" (").append(String.format("%.2f", total * 100 / (double) max)).append("%)\n");
					sb.append(ctx.translate("cmdtext_core_runtime", "gc_run")).append(' ')
							.append(memStats.elapsedSinceLastGC()
									.map(t -> ctx.translate("cmdtext_core_runtime", "ago", DurationUtils.format(t)))
									.orElse("Never"))
							.append("\n");
					sb.append(ctx.translate("cmdtext_core_runtime", "ram_after_gc")).append(' ').append(SystemUnit.format(used))
							.append(" (").append(String.format("%.2f", used * 100 / (double) max)).append("%)\n");
					return new EmbedField(ctx.translate("cmdtext_core_runtime", "memory_usage"), sb.toString());
				});
	}
	
	private static Mono<EmbedField> shardInfo(Context ctx) {
		var shardInfo = ctx.event().getShardInfo();
		return Mono.just(new EmbedField(ctx.translate("cmdtext_core_runtime", "gateway_sharding_info"),
				ctx.translate("cmdtext_core_runtime", "shard_index", shardInfo.getIndex()) + '\n'
				+ ctx.translate("cmdtext_core_runtime", "shard_count", shardInfo.getCount())));
	}
	
	private static Mono<EmbedField> cacheInfo(Context ctx) {
		final String[] storeNames = {
				ctx.translate("cmdtext_core_runtime", "channels"),
				ctx.translate("cmdtext_core_runtime", "emojis"),
				ctx.translate("cmdtext_core_runtime", "guilds"),
				ctx.translate("cmdtext_core_runtime", "messages"),
				ctx.translate("cmdtext_core_runtime", "members"),
				ctx.translate("cmdtext_core_runtime", "presences"),
				ctx.translate("cmdtext_core_runtime", "roles"),
				ctx.translate("cmdtext_core_runtime", "users"),
				ctx.translate("cmdtext_core_runtime", "voice_states")
		};
		var stateView = ctx.bot().gateway().getGatewayResources().getStateView();
		return Mono.zip(
				objArray -> Arrays.stream(objArray).map(x -> (Long) x).collect(Collectors.toList()),
				stateView.getChannelStore().count(),
				stateView.getGuildEmojiStore().count(),
				stateView.getGuildStore().count(),
				stateView.getMessageStore().count(),
				stateView.getMemberStore().count(),
				stateView.getPresenceStore().count(),
				stateView.getRoleStore().count(),
				stateView.getUserStore().count(),
				stateView.getVoiceStateStore().count())
			.map(counts -> {
				var sb = new StringBuilder();
				var i = 0;
				for (var count : counts) {
					sb.append(storeNames[i]).append(": ").append(count).append("\n");
					i++;
				}
				return sb.toString();
			})
			.map(content -> new EmbedField(ctx.translate("cmdtext_core_runtime", "cache_usage"), content));
	}
	
	private static class EmbedField {
		private final String title;
		private final String content;
		
		private EmbedField(String title, String content) {
			this.title = title;
			this.content = content;
		}
	}
}
