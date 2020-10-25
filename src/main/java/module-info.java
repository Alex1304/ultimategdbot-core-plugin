import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.service.ServiceDeclarator;
import com.github.alex1304.ultimategdbot.core.CorePlugin;
import com.github.alex1304.ultimategdbot.core.CoreServices;

module ultimategdbot.core {
	opens com.github.alex1304.ultimategdbot.core;
	opens com.github.alex1304.ultimategdbot.core.database;

	requires io.netty.codec.http;
	requires java.compiler;
	requires java.desktop;
	requires java.management;
	requires jdk.management;
	requires reactor.extra;
	requires ultimategdbot.api;

	requires static org.immutables.value;

	provides Plugin with CorePlugin;
	provides ServiceDeclarator with CoreServices;
}