package com.swirlds.platform.poc.moduledefs;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.poc.framework.TaskProcessor;

@FunctionalInterface
public interface Developer extends TaskProcessor {
	void implementFeature(String s) throws InterruptedException;

	@Override
	default InterruptableConsumer<?> getProcessingMethod() {
		return (InterruptableConsumer<String>)this::implementFeature;
	}
}
