package com.swirlds.platform.componentframework;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A test {@link LongProcessor} that just sets an atomic variable
 */
public class LongProcessorImpl implements LongProcessor {
	public static final long INITIAL_LONG = 0;
	private final AtomicLong lastProcessed = new AtomicLong(INITIAL_LONG);

	@Override
	public void processLong(final long l) {
		lastProcessed.set(l);
	}

	public long getLastProcessed() {
		return lastProcessed.get();
	}
}
