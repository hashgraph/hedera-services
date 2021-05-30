package com.hedera.services.stream;

import com.hedera.services.context.properties.NodeLocalProperties;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class NonBlockingHandoff {
	private static final int MIN_CAPACITY = 5_000;

	private final ExecutorService executor = newSingleThreadExecutor();
	private final RecordStreamManager recordStreamManager;
	private final BlockingQueue<RecordStreamObject> queue;

	public NonBlockingHandoff(RecordStreamManager recordStreamManager, NodeLocalProperties nodeLocalProperties) {
		this.recordStreamManager = recordStreamManager;

		final int capacity = Math.max(MIN_CAPACITY, nodeLocalProperties.recordStreamQueueCapacity());
		queue = new ArrayBlockingQueue<>(capacity);
		executor.execute(this::handoff);
		Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));
	}

	public boolean offer(RecordStreamObject rso) {
		return queue.offer(rso);
	}

	private void handoff() {
		for (;;) {
			final var rso = queue.poll();
			if (rso != null) {
				recordStreamManager.addRecordStreamObject(rso);
			}
		}
	}

	ExecutorService getExecutor() {
		return executor;
	}
}
