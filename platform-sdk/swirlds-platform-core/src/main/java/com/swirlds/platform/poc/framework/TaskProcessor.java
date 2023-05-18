package com.swirlds.platform.poc.framework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

public interface TaskProcessor extends Component {
	@Override
	default ComponentType getType() {
		return ComponentType.TASK_PROCESSOR;
	}

	InterruptableConsumer<?> getProcessingMethod();


}
