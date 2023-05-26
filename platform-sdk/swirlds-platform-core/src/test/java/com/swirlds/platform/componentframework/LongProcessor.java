package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.componentframework.framework.TaskProcessor;

import java.util.Map;

public interface LongProcessor extends TaskProcessor {
	void processLong(long l) throws InterruptedException;
	@Override
	default Map<Class<?>, InterruptableConsumer<?>> getProcessingMethods() {
		return Map.of(Long.class, (InterruptableConsumer<Long>) this::processLong);
	}
}
