package com.swirlds.platform.poc;

import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.platform.poc.framework.Component;
import com.swirlds.common.threading.utility.MultiHandler;
import com.swirlds.platform.poc.framework.MultiTaskProcessor;
import com.swirlds.platform.poc.framework.QueueSubmitter;
import com.swirlds.platform.poc.framework.TaskProcessor;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Wiring {
	private final List<Class<? extends Component>> componentsDefs;
	private final Map<Class<? extends Component>, BlockingQueue<Object>> queues;
	private final Map<Class<? extends Component>, Component> processors;
	private final Map<Class<? extends Component>, Object> facades;

	public Wiring(final List<Class<? extends Component>> componentsDefs) {
		this.componentsDefs = componentsDefs;
		queues = new HashMap<>();
		processors = new HashMap<>();
		facades = new HashMap<>();

		for (Class<? extends Component> component : componentsDefs) {
			if (TaskProcessor.class.isAssignableFrom(component)
			|| MultiTaskProcessor.class.isAssignableFrom(component)) {
				BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
				Object proxy = Proxy.newProxyInstance(
						Wiring.class.getClassLoader(),
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
		if (component instanceof TaskProcessor || component instanceof MultiTaskProcessor) {
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
				TaskProcessor tp = (TaskProcessor) processors.get(componentsDef);
				new QueueThreadConfiguration<>(AdHocThreadManager.getStaticThreadManager())
						.setQueue(Objects.requireNonNull(queues.get(componentsDef)))
						.setHandler((InterruptableConsumer<Object>) tp.getProcessingMethod())
						.build(true);
			}

			if (MultiTaskProcessor.class.isAssignableFrom(componentsDef)) {
				MultiTaskProcessor tp = (MultiTaskProcessor) processors.get(componentsDef);
				MultiHandler mh = new MultiHandler(tp.getProcessingMethods());
				new QueueThreadConfiguration<>(AdHocThreadManager.getStaticThreadManager())
						.setQueue(Objects.requireNonNull(queues.get(componentsDef)))
						.setHandler(mh::handle)
						.build(true);
			}
		}
	}
}
