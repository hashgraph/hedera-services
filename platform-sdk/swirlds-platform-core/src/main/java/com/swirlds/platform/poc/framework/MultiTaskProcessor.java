package com.swirlds.platform.poc.framework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public interface MultiTaskProcessor extends Component {
	@Override
	default ComponentType getType() {
		return ComponentType.MULTI_TASK_PROCESSOR;
	}

	List<Pair<Class<?>, InterruptableConsumer<?>>> getProcessingMethods();
}
