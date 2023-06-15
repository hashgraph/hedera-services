package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

import java.util.Map;

/**
 * A test {@link TaskProcessor} that processes {@link Long}s.
 */
public interface LongProcessor extends TaskProcessor {
	void processLong(long l) throws InterruptedException;

	@Override
	default Map<Class<?>, InterruptableConsumer<?>> getProcessingMethods() {
		return Map.of(Long.class, (InterruptableConsumer<Long>) this::processLong);
	}
}
