package com.hedera.services.state.logic;

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

import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamObject;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RecordStreaming implements Runnable {
	private final BlockManager blockManager;
	private final RecordsHistorian recordsHistorian;
	private final NonBlockingHandoff nonBlockingHandoff;

	@Inject
	public RecordStreaming(
			final BlockManager blockManager,
			final RecordsHistorian recordsHistorian,
			final NonBlockingHandoff nonBlockingHandoff
	) {
		this.blockManager = blockManager;
		this.recordsHistorian = recordsHistorian;
		this.nonBlockingHandoff = nonBlockingHandoff;
	}

	@Override
	public void run() {
		final var blockNo = blockManager.getManagedBlockNumberAt(recordsHistorian.firstUsedTimestamp());

		if (recordsHistorian.hasPrecedingChildRecords()) {
			for (final var childRso : recordsHistorian.getPrecedingChildRecords()) {
				stream(childRso.withBlockNumber(blockNo));
			}
		}
		stream(recordsHistorian.getTopLevelRecord().withBlockNumber(blockNo));
		if (recordsHistorian.hasFollowingChildRecords()) {
			for (final var childRso : recordsHistorian.getFollowingChildRecords()) {
				stream(childRso.withBlockNumber(blockNo));
			}
		}

		blockManager.updateCurrentBlockHash(recordsHistorian.lastRunningHash());
	}

	public void stream(final RecordStreamObject rso) {
		while (!nonBlockingHandoff.offer(rso)) {
			/* Cannot proceed until we have handed off the record. */
		}
	}
}