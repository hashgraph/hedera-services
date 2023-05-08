package com.swirlds.platform.modules;

import java.util.List;

public class Poc {
	public static void main(String[] args) {
		final List<Module> modules = List.of(
				new Module(Module1.class, ModuleType.TASK_PROCESSOR),
				new Module(Module2.class, ModuleType.TASK_PROCESSOR),
				new Module(Nexus1.class, ModuleType.NEXUS),
				new Module(Nexus2.class, ModuleType.NEXUS)
		);

	}
}
