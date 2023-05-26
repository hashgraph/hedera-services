package com.swirlds.platform.componentframework.framework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public interface MultiTaskProcessor extends Component {

	List<Pair<Class<?>, InterruptableConsumer<?>>> getProcessingMethods();
}
