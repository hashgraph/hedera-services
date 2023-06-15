package com.swirlds.platform.componentframework.internal;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.componentframework.TaskProcessor;
import com.swirlds.platform.componentframework.TaskProcessorConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.concurrent.BlockingQueue;

/**
 * A container for the parts of a {@link TaskProcessor} that are needed by the framework
 */
public class ProcessorParts {
	private final Class<? extends TaskProcessor> definition;
	private final TaskProcessorConfig config;
	private final BlockingQueue<Object> queue;
	private final TaskProcessor submitter;
	private TaskProcessor implementation;
	private QueueThread<?> queueThread;

	/**
	 * Create a new container for the parts of a {@link TaskProcessor}
	 *
	 * @param definition
	 * 		the definition of the task processor
	 * @param config
	 * 		the configuration for the task processor
	 * @param queue
	 * 		the queue for the task processor
	 * @param submitter
	 * 		the submitter for the task processor
	 */
	public ProcessorParts(
			@NonNull final Class<? extends TaskProcessor> definition,
			@NonNull final TaskProcessorConfig config,
			@NonNull final BlockingQueue<Object> queue,
			@NonNull final TaskProcessor submitter) {
		this.definition = definition;
		this.config = config;
		this.queue = queue;
		this.submitter = submitter;
	}

	/**
	 * Set the implementation for this task processor
	 *
	 * @param implementation
	 * 		the implementation for this task processor
	 */
	public void setImplementation(@NonNull final TaskProcessor implementation) {
		if (this.implementation != null) {
			throw new IllegalStateException("Implementation already set");
		}
		this.implementation = implementation;
	}

	/**
	 * Set the queue thread for this task processor
	 *
	 * @param queueThread
	 * 		the queue thread for this task processor
	 */
	public void setQueueThread(@NonNull final QueueThread<?> queueThread) {
		this.queueThread = queueThread;
	}


	/**
	 * @return the definition of the task processor
	 */
	public @NonNull Class<? extends TaskProcessor> getDefinition() {
		return definition;
	}

	/**
	 * @return the configuration for the task processor
	 */
	public @NonNull TaskProcessorConfig getConfig() {
		return config;
	}

	/**
	 * @return the queue for the task processor
	 */
	public @NonNull BlockingQueue<Object> getQueue() {
		return queue;
	}

	/**
	 * @return the submitter for the task processor
	 */
	public @NonNull TaskProcessor getSubmitter() {
		return submitter;
	}

	/**
	 * @return the implementation for this task processor
	 */
	public @Nullable TaskProcessor getImplementation() {
		return implementation;
	}

	/**
	 * @return the queue thread for this task processor
	 */
	public @Nullable QueueThread<?> getQueueThread() {
		return queueThread;
	}
}
