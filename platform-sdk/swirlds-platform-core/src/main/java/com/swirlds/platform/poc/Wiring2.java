package com.swirlds.platform.poc;

import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.platform.poc.infrastructure.Component;
import com.swirlds.platform.poc.infrastructure.QueueSubmitter;
import com.swirlds.platform.poc.infrastructure.TaskProcessor;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Wiring2 {
	private final List<Class<? extends Component>> componentsDefs;
	private final Map<Class<? extends Component>, BlockingQueue<Object>> queues;
	private final Map<Class<? extends Component>, TaskProcessor> processors;
	private final Map<Class<? extends Component>, Object> facades;

	public Wiring2(final List<Class<? extends Component>> componentsDefs) {
		this.componentsDefs = componentsDefs;
		queues = new HashMap<>();
		processors = new HashMap<>();
		facades = new HashMap<>();

		for (Class<? extends Component> component : componentsDefs) {
			if (TaskProcessor.class.isAssignableFrom(component)) {
				BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
				Object proxy = Proxy.newProxyInstance(
						Wiring2.class.getClassLoader(),
						new Class[] { component },
						new QueueSubmitter(queue));
				facades.put(component, proxy);
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
		if (component instanceof TaskProcessor tp) {
			processors.put(componentClass, tp);
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
				TaskProcessor tp = processors.get(componentsDef);
				new QueueThreadConfiguration<>(AdHocThreadManager.getStaticThreadManager())
						.setQueue(Objects.requireNonNull(queues.get(componentsDef)))
						.setHandler((InterruptableConsumer<Object>) tp.getProcessingMethod())
						.build(true);
			}
		}
	}
}
