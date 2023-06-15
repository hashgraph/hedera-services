package com.swirlds.platform.componentframework.internal;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.componentframework.TaskProcessor;
import com.swirlds.platform.componentframework.TaskProcessorConfig;

import java.util.concurrent.BlockingQueue;

public class ProcessorParts {
	private final Class<? extends TaskProcessor> definition;
	private final TaskProcessorConfig config;
	private final BlockingQueue<Object> queue;
	private final TaskProcessor submitter;
	private TaskProcessor implementation;
	private QueueThread<?> queueThread;

	public ProcessorParts(final Class<? extends TaskProcessor> definition, final TaskProcessorConfig config,
			final BlockingQueue<Object> queue,
			final TaskProcessor submitter) {
		this.definition = definition;
		this.config = config;
		this.queue = queue;
		this.submitter = submitter;
	}

	public void setImplementation(final TaskProcessor implementation) {
		if (this.implementation != null) {
			throw new IllegalStateException("Implementation already set");
		}
		this.implementation = implementation;
	}

	public void setQueueThread(final QueueThread<?> queueThread) {
		this.queueThread = queueThread;
	}

	public boolean isComplete() {
		return implementation != null;
	}

	public Class<? extends TaskProcessor> getDefinition() {
		return definition;
	}

	public TaskProcessorConfig getConfig() {
		return config;
	}

	public BlockingQueue<Object> getQueue() {
		return queue;
	}

	public TaskProcessor getSubmitter() {
		return submitter;
	}

	public TaskProcessor getImplementation() {
		return implementation;
	}

	public QueueThread<?> getQueueThread() {
		return queueThread;
	}
}
