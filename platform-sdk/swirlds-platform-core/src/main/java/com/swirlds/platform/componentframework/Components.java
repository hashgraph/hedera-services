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
	private final List<Class<? extends Component>> componentsDefs;
	private final Map<Class<? extends Component>, BlockingQueue<Object>> queues;
	private final Map<Class<? extends Component>, Component> processors;
	private final Map<Class<? extends Component>, Object> facades;

	public Components(final List<Class<? extends Component>> componentsDefs) {
		this.componentsDefs = componentsDefs;
		queues = new HashMap<>();
		processors = new HashMap<>();
		facades = new HashMap<>();

		for (Class<? extends Component> component : componentsDefs) {
			if (TaskProcessor.class.isAssignableFrom(component)) {
				BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
				facades.put(component, QueueSubmitter.create(component, queue));
				queues.put(component, queue);
			}
		}
	}

	public <T extends Component> void addImplementation(T component) {
		final Class<? extends Component> defClass = componentsDefs
				.stream()
				.filter(c -> c.isAssignableFrom(component.getClass()))
				.findFirst()
				.orElseThrow();
		addImplementation(
				component,
				(Class<T>) defClass
		);
	}

	public <T extends Component> void addImplementation(T component, Class<T> componentClass){
		if (component instanceof TaskProcessor) {
			processors.put(componentClass, component);
		} else {
			facades.put(componentClass, component);
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends Component> T getComponent(Class<T> componentClass) {
		return (T) facades.get(componentClass);
	}

	@SuppressWarnings("unchecked")
	public void start(){
		for (Class<? extends Component> componentsDef : componentsDefs) {
			if (TaskProcessor.class.isAssignableFrom(componentsDef)) {
				final TaskProcessor tp = (TaskProcessor) processors.get(componentsDef);
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
