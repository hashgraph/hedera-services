package com.hedera.services.state.tasks;

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

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.state.tasks.SystemTask.RELEASE_0260_VERSION;
import static com.hedera.services.state.tasks.SystemTask.RUNTIME_CONSTRUCTABLE_ID;
import static com.hedera.services.state.tasks.SystemTaskType.DISSOCIATED_NFT_REMOVALS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class SystemTaskTest {
	private final long accountNum = 1001L;
	private final long tokenNum = 1002L;
	private final long serialsCount = 1003L;
	private final long headNftTokenNum = 1004L;
	private final long headSerialNum = 1005L;
	private final SystemTaskType taskType = DISSOCIATED_NFT_REMOVALS;
	private final DissociateNftRemovals nftRemovalTask = new DissociateNftRemovals(
			accountNum, tokenNum, serialsCount, headNftTokenNum, headSerialNum);
	private SystemTask subject;

	@BeforeEach
	void setUp() {
		subject = new SystemTask(taskType, nftRemovalTask);
	}

	@Test
	void metaAsExpected() {
		assertEquals(RELEASE_0260_VERSION, subject.getVersion());
		assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void copyReturnsSelf() {
		assertEquals(subject, subject.copy());
	}

	@Test
	void hashableMethodsWork() {
		final var pretend = mock(Hash.class);

		subject.setHash(pretend);

		assertEquals(pretend, subject.getHash());
	}

	@Test
	void gettersAndSettersWork() {
		subject = new SystemTask();

		subject.setTaskType(taskType);
		subject.setSerializableTask(nftRemovalTask);

		assertEquals(taskType, subject.getTaskType());
		assertEquals(nftRemovalTask, subject.getSerializableTask());
	}

	@Test
	void equalsWorks() {
		final DissociateNftRemovals otherNftRemovalTask = new DissociateNftRemovals(
				accountNum, tokenNum, serialsCount+1, headNftTokenNum, headSerialNum);
		final var subject2 = new SystemTask();
		final var subject3 = new SystemTask(DISSOCIATED_NFT_REMOVALS, otherNftRemovalTask);
		final var identicalSubject = new SystemTask(DISSOCIATED_NFT_REMOVALS, nftRemovalTask);

		assertEquals(subject, subject);
		assertEquals(subject, identicalSubject);
		assertNotEquals(subject, subject2);
		assertNotEquals(subject, subject3);
		assertNotEquals(null, subject);
	}
}
