package com.github.alex1304.ultimategdbot.core;

abstract class CoreCommand {
	
	CoreService core;

	void setCoreService(CoreService coreService) {
		this.core = coreService;
	}
}
