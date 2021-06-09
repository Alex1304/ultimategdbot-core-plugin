package com.github.alex1304.ultimategdbot.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.core.database.BlacklistedIdDao;
import com.github.alex1304.ultimategdbot.core.database.BotAdminDao;
import com.github.alex1304.ultimategdbot.core.database.CoreConfigDao;
import com.github.alex1304.ultimategdbot.core.database.mongo.*;
import com.mongodb.reactivestreams.client.MongoClients;
import discord4j.common.jackson.UnknownPropertyHandler;
import org.immutables.criteria.mongo.MongoBackend;
import org.immutables.criteria.mongo.MongoSetup;
import org.immutables.criteria.mongo.bson4jackson.BsonModule;
import org.immutables.criteria.mongo.bson4jackson.IdAnnotationModule;
import org.immutables.criteria.mongo.bson4jackson.JacksonCodecs;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

import static reactor.function.TupleUtils.function;

@CommandDescriptor(
        aliases = "migratedb"
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
public final class MigrateDBCommand {

    @Root
    private CoreService core;

    @CommandAction
    public Mono<Void> runAdd(Context ctx, String dbName) {
        return Mono.defer(() -> {
            final var mapper = new ObjectMapper()
                    .registerModule(new BsonModule())
                    .registerModule(new Jdk8Module())
                    .registerModule(new IdAnnotationModule())
                    .addHandler(new UnknownPropertyHandler(true));
            @SuppressWarnings("UnstableApiUsage") final var registry = JacksonCodecs.registryFromMapper(mapper);
            final var mongoClient = MongoClients.create();
            final var db = mongoClient.getDatabase(dbName).withCodecRegistry(registry);
            final var backend = new MongoBackend(MongoSetup.of(db));
            final var blacklistRepo = new BlacklistRepository(backend);
            final var botAdminRepo = new BotAdminRepository(backend);
            final var guildConfigRepo = new GuildConfigRepository(backend);
            return Mono.zip(
                    core.bot().database().withExtension(BlacklistedIdDao.class, BlacklistedIdDao::getAll),
                    core.bot().database().withExtension(BotAdminDao.class, BotAdminDao::getAll),
                    core.bot().database().withExtension(CoreConfigDao.class, CoreConfigDao::getAll))
                    .flatMap(function((blacklist, botAdmin, coreConfig) -> Mono.when(
                            blacklist.isEmpty() ? Mono.empty() : blacklistRepo.upsertAll(blacklist.stream()
                                    .map(ImmutableBlacklist::of)
                                    .collect(Collectors.toList())),
                            botAdmin.isEmpty() ? Mono.empty() : botAdminRepo.upsertAll(botAdmin.stream()
                                    .map(ImmutableBotAdmin::of)
                                    .collect(Collectors.toList())),
                            coreConfig.isEmpty() ? Mono.empty() : guildConfigRepo.upsertAll(coreConfig.stream()
                                    .map(data -> ImmutableGuildConfig.builder()
                                            .guildId(data.guildId().asLong())
                                            .locale(data.locale())
                                            .prefix(data.prefix())
                                            .build())
                                    .collect(Collectors.toList()))
                    )))
                    .doFinally(signal -> mongoClient.close())
                    .then(ctx.reply("Success!"))
                    .then();
        });
    }
}
