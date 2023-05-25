package com.swirlds.platform.components.state;

import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.state.signed.SignedState;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TestStateToDiskAttemptConsumer implements StateToDiskAttemptConsumer {
	private final BlockingQueue<StateToDiskAttempt> queue = new LinkedBlockingQueue<>();

	@Override
	public void stateToDiskAttempt(final SignedState signedState, final Path directory, final boolean success) {
		queue.offer(new StateToDiskAttempt(signedState, directory, success));
	}

	public BlockingQueue<StateToDiskAttempt> getAttemptQueue() {
		return queue;
	}
}
