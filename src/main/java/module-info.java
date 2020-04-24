import com.github.alex1304.ultimategdbot.api.PluginBootstrap;
import com.github.alex1304.ultimategdbot.core.CorePluginBootstrap;

module ultimategdbot.core {
	opens com.github.alex1304.ultimategdbot.core;
	opens com.github.alex1304.ultimategdbot.core.database;
	
	requires java.compiler;
	requires java.desktop;
	requires java.management;
	requires jdk.management;
	requires reactor.extra;
	requires ultimategdbot.api;

	requires static com.google.errorprone.annotations;
	requires static org.immutables.value;

	provides PluginBootstrap with CorePluginBootstrap;
}