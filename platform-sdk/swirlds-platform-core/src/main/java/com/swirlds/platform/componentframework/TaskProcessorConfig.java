package com.swirlds.platform.componentframework;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.concurrent.BlockingQueue;

/**
 * Configuration for a {@link TaskProcessor}.
 *
 * @param definition
 * 		an interface that extends {@link TaskProcessor} and describes which tasks the processor can handle
 * @param name
 * 		a name for the processor
 * @param customQueue
 * 		a custom queue to use for the processor, or null to use the default queue
 */
public record TaskProcessorConfig(
		@NonNull Class<? extends TaskProcessor> definition,
		@NonNull String name,
		@Nullable BlockingQueue<Object> customQueue) {
}
