package com.swirlds.platform.componentframework.framework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;

import java.util.Map;

public interface TaskProcessor extends Component {

	Map<Class<?>, InterruptableConsumer<?>> getProcessingMethods();


}
