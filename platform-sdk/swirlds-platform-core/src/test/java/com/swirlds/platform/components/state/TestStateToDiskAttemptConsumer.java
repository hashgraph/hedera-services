package com.swirlds.platform.components.state;

import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.state.signed.SignedState;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A {@link StateToDiskAttemptConsumer} that stores the {@link StateToDiskAttempt}s in a {@link BlockingQueue} for testing purposes
 */
public class TestStateToDiskAttemptConsumer implements StateToDiskAttemptConsumer {
	private final BlockingQueue<StateToDiskAttempt> queue = new LinkedBlockingQueue<>();

	@Override
	public void stateToDiskAttempt(@NonNull final SignedState signedState, @NonNull final Path directory, final boolean success) {
		try {
			queue.put(new StateToDiskAttempt(signedState, directory, success));
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public @NonNull BlockingQueue<StateToDiskAttempt> getAttemptQueue() {
		return queue;
	}
}
