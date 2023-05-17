package com.swirlds.platform.poc;

import com.swirlds.platform.poc.infrastructure.TaskModule;

import java.util.List;

public interface Module1 extends TaskModule {
	@Override
	default List<Class<?>> getHandledTypes() {
		return null;
	}
}
