import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.service.ServiceDeclarator;
import com.github.alex1304.ultimategdbot.core.CorePlugin;
import com.github.alex1304.ultimategdbot.core.CoreServices;

module ultimategdbot.core {
	opens com.github.alex1304.ultimategdbot.core;
	opens com.github.alex1304.ultimategdbot.core.database;
	opens com.github.alex1304.ultimategdbot.core.database.mongo;

	requires io.netty.codec.http;
	requires java.compiler;
	requires java.desktop;
	requires java.management;
	requires jdk.management;
    requires org.mongodb.driver.reactivestreams;
    requires org.immutables.criteria.common;
    requires org.immutables.criteria.mongo;
    requires org.immutables.criteria.reactor;
    requires org.mongodb.bson;
	requires reactor.extra;
	requires ultimategdbot.api;

	requires static com.google.errorprone.annotations;
	requires static org.immutables.value;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jdk8;

    provides Plugin with CorePlugin;
	provides ServiceDeclarator with CoreServices;
}