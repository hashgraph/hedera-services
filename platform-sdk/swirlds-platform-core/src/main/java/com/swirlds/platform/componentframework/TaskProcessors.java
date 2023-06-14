package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.common.threading.utility.MultiHandler;
import com.swirlds.platform.componentframework.internal.ProcessorParts;
import com.swirlds.platform.componentframework.internal.QueueSubmitter;
import com.swirlds.platform.componentframework.internal.TaskProcessorUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TaskProcessors {

	private final Map<Class<? extends TaskProcessor>, ProcessorParts> parts = new HashMap<>();
	private final QueueThreadConfiguration<Object> defaultConfiguration;
	private boolean started = false;

	public TaskProcessors(final List<Class<? extends TaskProcessor>> taskProcessorDefs) {
		this(
				new QueueThreadConfiguration<>(AdHocThreadManager.getStaticThreadManager()),
				taskProcessorDefs.stream()
						.map(d -> new TaskProcessorConfig(d, "a name", null))
						.toList()
		);
	}

	public TaskProcessors(
			final QueueThreadConfiguration<Object> defaultConfiguration,
			final List<TaskProcessorConfig> taskProcessorDefs) {
		this.defaultConfiguration = Objects.requireNonNull(defaultConfiguration);
		Objects.requireNonNull(taskProcessorDefs);
		if (taskProcessorDefs.isEmpty()) {
			throw new IllegalArgumentException("Must supply at least one TaskProcessor definition");
		}
		taskProcessorDefs.stream()
				.map(TaskProcessorConfig::definition)
				.forEach(TaskProcessorUtils::checkTaskProcessorDefinition);

		for (final TaskProcessorConfig config : taskProcessorDefs) {
			final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
			if (parts.containsKey(config.definition())) {
				throw new IllegalArgumentException(String.format(
						"Duplicate TaskProcessor definition: %s",
						config.definition().getName()
				));
			}
			parts.put(
					config.definition(),
					new ProcessorParts(
							config.definition(),
							config,
							queue,
							QueueSubmitter.create(config.definition(), queue)));
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
					defaultConfiguration
							.copy()
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

	private void throwIfNotStarted() {
		if (!started) {
			throw new IllegalStateException("Cannot perform this operation before starting");
		}
	}
}
