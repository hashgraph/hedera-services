package com.swirlds.platform.modules;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

public interface TaskModule<T> {
	InterruptableConsumer<T> getTaskProcessor();
}
