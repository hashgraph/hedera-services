package com.swirlds.platform.poc;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

public interface Module2 extends TaskModule<String> {
	void process(String s) throws InterruptedException;

	@Override
	default InterruptableConsumer<String> getTaskProcessor() {
		return this::process;
	}
}
