package com.swirlds.platform.poc.infrastructure;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

import java.util.List;

public interface TaskProcessor extends Component {
	@Override
	default ComponentType getType() {
		return ComponentType.TASK_PROCESSOR;
	}

	InterruptableConsumer<?> getProcessingMethod();

	default <T> InterruptableConsumer<?> erase(InterruptableConsumer<T> consumer){
		return consumer;
	}
}
