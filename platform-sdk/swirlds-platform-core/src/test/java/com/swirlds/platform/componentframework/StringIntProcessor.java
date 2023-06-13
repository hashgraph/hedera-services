package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

import java.util.Map;

public interface StringIntProcessor extends TaskProcessor {
	void string(String s) throws InterruptedException;
	void number(Integer i) throws InterruptedException;

	@Override
	default Map<Class<?>, InterruptableConsumer<?>> getProcessingMethods() {
		return Map.of(
				String.class,
				(InterruptableConsumer<String>) this::string,
				Integer.class,
				(InterruptableConsumer<Integer>) this::number
		);
	}
}
