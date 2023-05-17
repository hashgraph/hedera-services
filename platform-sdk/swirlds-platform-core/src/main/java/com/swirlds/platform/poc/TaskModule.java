package com.swirlds.platform.poc;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

public interface TaskModule<T> {
	InterruptableConsumer<T> getTaskProcessor();
}
