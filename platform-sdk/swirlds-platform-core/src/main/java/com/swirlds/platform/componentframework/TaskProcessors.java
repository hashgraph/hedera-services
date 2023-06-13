package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.common.threading.utility.MultiHandler;
import com.swirlds.platform.componentframework.internal.ProcessorParts;
import com.swirlds.platform.componentframework.internal.QueueSubmitter;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TaskProcessors {
	private static final Set<String> IGNORED_METHODS = Set.of("getProcessingMethods");
	private final Map<Class<? extends TaskProcessor>, ProcessorParts> parts;
	private boolean started = false;

	public TaskProcessors(final List<Class<? extends TaskProcessor>> taskProcessorDefs) {
		Objects.requireNonNull(taskProcessorDefs);
		if (taskProcessorDefs.isEmpty()) {
			throw new IllegalArgumentException("Must supply at least one TaskProcessor definition");
		}

		for (Class<? extends TaskProcessor> def : taskProcessorDefs) {
			if (!def.isInterface()) {
				throw new IllegalArgumentException(String.format(
						"A TaskProcessor must be an interface. %s is not an interface",
						def.getName()
				));
			}
			final Method[] methods = def.getDeclaredMethods();
			for (final Method method : methods) {
				if (IGNORED_METHODS.contains(method.getName())) {
					continue;
				}
				if (method.getParameterCount() != 1) {
					throw new IllegalArgumentException(String.format(
							"A TaskProcessor method must have exactly one parameter. The method %s is invalid",
							method
					));
				}
				if (method.getReturnType() != void.class) {
					throw new IllegalArgumentException(String.format(
							"A TaskProcessor method must return void. The method %s is invalid",
							method
					));
				}
			}
		}

		parts = new HashMap<>();

		for (Class<? extends TaskProcessor> def : taskProcessorDefs) {
			BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
			if (parts.containsKey(def)) {
				throw new IllegalArgumentException(String.format(
						"Duplicate TaskProcessor definition: %s",
						def.getName()
				));
			}
			parts.put(def, new ProcessorParts(def, queue, QueueSubmitter.create(def, queue)));
		}
	}

	public <T extends TaskProcessor> void addImplementation(final T implementation) {
		throwIfStarted();
		Objects.requireNonNull(implementation);
		parts.values()
				.stream()
				.filter(p -> p.getDefinition().isAssignableFrom(implementation.getClass()))
				.reduce((a, b) -> {
					throw new IllegalArgumentException(String.format(
							"Implementing class %s implements multiple TaskProcessor definitions: %s and %s",
							implementation.getClass().getName(),
							a.getDefinition().getName(),
							b.getDefinition().getName()
					));
				})
				.orElseThrow(() -> new IllegalArgumentException(String.format(
						"Implementing class %s does not implement any TaskProcessor definitions",
						implementation.getClass().getName()
				)))
				.setImplementation(implementation);
	}

	public <T extends TaskProcessor> void addImplementation(T component, Class<T> componentClass) {
		throwIfStarted();
		Objects.requireNonNull(component);
		Objects.requireNonNull(componentClass);
		parts.get(componentClass).setImplementation(component);
	}

	@SuppressWarnings("unchecked")
	public <T extends TaskProcessor> T getSubmitter(Class<T> processorClass) {
		Objects.requireNonNull(processorClass);
		return (T) parts.get(processorClass).getSubmitter();
	}

	@SuppressWarnings("unchecked")
	public void start() {
		throwIfStarted();
		parts.values().stream().filter(p -> p.getImplementation() == null).forEach(p -> {
			throw new IllegalStateException(String.format(
					"TaskProcessor %s has no implementation set",
					p.getDefinition().getName()
			));
		});

		for (final ProcessorParts processorParts : parts.values()) {
			final Map<Class<?>, InterruptableConsumer<?>> processingMethods =
					processorParts.getImplementation().getProcessingMethods();
			final InterruptableConsumer<?> handler;
			if (processingMethods.size() == 1) {
				handler = processingMethods.values().iterator().next();
			} else {
				handler = new MultiHandler(processingMethods)::handle;
			}

			final QueueThread<Object> queueThread =
					new QueueThreadConfiguration<>(AdHocThreadManager.getStaticThreadManager())
							.setQueue(processorParts.getQueue())
							.setHandler((InterruptableConsumer<Object>) handler)
							.build(true);
			processorParts.setQueueThread(queueThread);
		}
		started = true;
	}

	public QueueThread<?> getQueueThread(final Class<? extends TaskProcessor> processorClass) {
		throwIfNotStarted();
		return parts.get(processorClass).getQueueThread();
	}

	public void stop() {
		throwIfNotStarted();
		parts.values().forEach(p -> p.getQueueThread().stop());
	}

	private void throwIfStarted() {
		if (started) {
			throw new IllegalStateException("Cannot perform this operation after starting");
		}
	}

	public void throwIfNotStarted() {
		if (!started) {
			throw new IllegalStateException("Cannot perform this operation before starting");
		}
	}
}
