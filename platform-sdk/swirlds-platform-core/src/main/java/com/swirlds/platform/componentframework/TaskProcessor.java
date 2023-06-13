package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

import java.util.Map;

public interface TaskProcessor {

	Map<Class<?>, InterruptableConsumer<?>> getProcessingMethods();


}
