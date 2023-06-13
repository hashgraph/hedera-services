package com.swirlds.platform.componentframework.internal;

import com.swirlds.platform.componentframework.TaskProcessor;

import java.util.concurrent.BlockingQueue;

public class ProcessorParts {
	private final Class<? extends TaskProcessor> definition;
	private final BlockingQueue<Object> queue;
	private final TaskProcessor submitter;
	private TaskProcessor implementation;

	public ProcessorParts(final Class<? extends TaskProcessor> definition, final BlockingQueue<Object> queue,
			final TaskProcessor submitter) {
		this.definition = definition;
		this.queue = queue;
		this.submitter = submitter;
	}

	public void setImplementation(final TaskProcessor implementation) {
		this.implementation = implementation;
	}

	public boolean isComplete() {
		return implementation != null;
	}

	public Class<? extends TaskProcessor> getDefinition() {
		return definition;
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
}
