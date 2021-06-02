package com.hedera.services.stream;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

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
