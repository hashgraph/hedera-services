package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.componentframework.framework.TaskProcessor;

import java.util.Map;

public interface StringIntProcessor extends TaskProcessor {
	void string(String s) throws InterruptedException;
	void number(Integer i) throws InterruptedException;

	@Override
	default Map<Class<?>, InterruptableConsumer<Object>> getProcessingMethods() {
		return Map.of(
				String.class,
				(InterruptableConsumer<Object>) this::string,
				Integer.class,
				(InterruptableConsumer<Object>) this::number
		);
	}
}
