package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.common.threading.utility.MultiHandler;
import com.swirlds.platform.componentframework.framework.Component;
import com.swirlds.platform.componentframework.framework.QueueSubmitter;
import com.swirlds.platform.componentframework.framework.TaskProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Components {
	private final List<Class<? extends TaskProcessor>> componentsDefs;
	private final Map<Class<? extends TaskProcessor>, BlockingQueue<Object>> queues;
	private final Map<Class<? extends TaskProcessor>, TaskProcessor> processors;
	private final Map<Class<? extends TaskProcessor>, Object> facades;

	public Components(final List<Class<? extends TaskProcessor>> componentsDefs) {
		this.componentsDefs = componentsDefs;
		queues = new HashMap<>();
		processors = new HashMap<>();
		facades = new HashMap<>();

		for (Class<? extends TaskProcessor> component : componentsDefs) {
			if (TaskProcessor.class.isAssignableFrom(component)) {
				BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
				facades.put(component, QueueSubmitter.create(component, queue));
				queues.put(component, queue);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends TaskProcessor> void addImplementation(T component) {
		Objects.requireNonNull(component);
		final Class<? extends TaskProcessor> defClass = componentsDefs
				.stream()
				.filter(c -> c.isAssignableFrom(component.getClass()))
				.findFirst()
				.orElseThrow();
		addImplementation(
				component,
				(Class<T>) defClass
		);
	}

	public <T extends TaskProcessor> void addImplementation(T component, Class<T> componentClass){
		Objects.requireNonNull(component);
		Objects.requireNonNull(componentClass);
		processors.put(componentClass, component);
	}

	@SuppressWarnings("unchecked")
	public <T extends Component> T getComponent(Class<T> componentClass) {
		Objects.requireNonNull(componentClass);
		return (T) facades.get(componentClass);
	}

	@SuppressWarnings("unchecked")
	public void start(){
		for (Class<? extends Component> componentsDef : componentsDefs) {
			if (TaskProcessor.class.isAssignableFrom(componentsDef)) {
				final TaskProcessor tp = processors.get(componentsDef);
				final Map<Class<?>, InterruptableConsumer<?>> processingMethods = tp.getProcessingMethods();
				final InterruptableConsumer<?> handler;
				if (processingMethods.size() == 1) {
					handler = processingMethods.values().iterator().next();
				} else {
					handler = new MultiHandler(processingMethods)::handle;
				}

				new QueueThreadConfiguration<>(AdHocThreadManager.getStaticThreadManager())
						.setQueue(Objects.requireNonNull(queues.get(componentsDef)))
						.setHandler((InterruptableConsumer<Object>) handler)
						.build(true);
			}
		}
	}
}
