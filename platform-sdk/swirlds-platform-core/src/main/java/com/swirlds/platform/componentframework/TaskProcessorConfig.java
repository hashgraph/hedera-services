package com.swirlds.platform.componentframework;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.concurrent.BlockingQueue;

public record TaskProcessorConfig(
		@NonNull Class<? extends TaskProcessor> definition,
		@NonNull String name,
		@Nullable BlockingQueue<?> customQueue){
}
