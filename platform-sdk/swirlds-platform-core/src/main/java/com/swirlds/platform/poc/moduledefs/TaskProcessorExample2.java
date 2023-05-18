package com.swirlds.platform.poc.moduledefs;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.platform.poc.infrastructure.TaskProcessor;

@FunctionalInterface
public interface TaskProcessorExample2 extends TaskProcessor {
	void process(String s) throws InterruptedException;

	@Override
	default InterruptableConsumer<?> getProcessingMethod() {
		return (InterruptableConsumer<String>)this::process;
	}
}
