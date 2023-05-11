package com.swirlds.platform.modules;

import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.AdHocThreadManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Wiring {
	private final Map<NexusDef<?>, Object> nexuses;
	private final Map<TaskProcessorDef<?>, InterruptableConsumer<?>> taskProcessors;
	private final Map<TaskProcessorDef<?>, BlockingQueue<?>> queues;

	public Wiring(final List<TaskProcessorDef<?>> taskProcessorDefs) {
		nexuses = new HashMap<>();
		taskProcessors = new HashMap<>();
		queues = new HashMap<>();

		for (TaskProcessorDef<?> taskProcessorDef : taskProcessorDefs) {
			queues.put(taskProcessorDef, new LinkedBlockingQueue<>());
		}
	}

	public <T> void addNexus(NexusDef<T> def, T nexus) {
		nexuses.put(def, nexus);
	}

	@SuppressWarnings("unchecked")
	public <T> T getNexus(NexusDef<T> def) {
		return (T) nexuses.get(def);
	}

	public <T> void addTaskProcessor(TaskProcessorDef<T> def, InterruptableConsumer<T> taskProcessor) {
		taskProcessors.put(def, taskProcessor);
	}

	public <T> void addModule(TaskProcessorDef<T> def, TaskModule<T> module) {
		taskProcessors.put(def, module.getTaskProcessor());
	}

	@SuppressWarnings("unchecked")
	public <T> InterruptableConsumer<T> getTaskSubmitter(TaskProcessorDef<T> def) {
		final BlockingQueue<T> queue = (BlockingQueue<T>) queues.get(def);
		return queue::put;
	}

	@SuppressWarnings("unchecked")
	public void start(){
		for (Map.Entry<TaskProcessorDef<?>, BlockingQueue<?>> entry : queues.entrySet()) {
			new QueueThreadConfiguration<>(AdHocThreadManager.getStaticThreadManager())
					.setQueue((BlockingQueue<Object>) entry.getValue())
					.setHandler((InterruptableConsumer<Object>) taskProcessors.get(entry.getKey()))
					.build(true);
		}
	}
}
