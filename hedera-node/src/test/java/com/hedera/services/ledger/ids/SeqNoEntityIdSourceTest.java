package com.hedera.services.ledger.ids;

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

import com.hedera.services.state.submerkle.SequenceNumber;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.asTopic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

class SeqNoEntityIdSourceTest {
	SequenceNumber seqNo;
	SeqNoEntityIdSource subject;

	@BeforeEach
	private void setup() {
		seqNo = mock(SequenceNumber.class);
		subject = new SeqNoEntityIdSource(() -> seqNo);
	}

	@Test
	void resetsToZero() {
		subject.newTokenId();
		subject.newTokenId();

		subject.resetProvisionalIds();

		assertEquals(0, subject.getProvisionalIds());
	}

	@Test
	void reclaimsAsExpected() {
		subject.newTokenId();
		subject.newTokenId();

		subject.reclaimProvisionalIds();

		assertEquals(0, subject.getProvisionalIds());
		verify(seqNo, times(2)).decrement();
	}

	@Test
	void returnsExpectedAccountId() {
		given(seqNo.getAndIncrement()).willReturn(555L);

		// when:
		final var newId = subject.newAccountId();

		// then:
		assertEquals(asAccount("0.0.555"), newId.toGrpcAccountId());
	}

	@Test
	void returnsExpectedFileId() {
		given(seqNo.getAndIncrement()).willReturn(555L);

		// when:
		FileID newId = subject.newFileId();

		// then:
		assertEquals(asFile("0.0.555"), newId);
	}

	@Test
	void returnsExpectedTokenId() {
		given(seqNo.getAndIncrement()).willReturn(555L);

		TokenID newId = subject.newTokenId();

		assertEquals(asToken("0.0.555"), newId);
	}

	@Test
	void returnsExpectedTopicId() {
		given(seqNo.getAndIncrement()).willReturn(222L);
		TopicID id = subject.newTopicId();
		assertEquals(asTopic("0.0.222"), id);
	}

	@Test
	void reclaimDecrementsId() {
		// when:
		subject.reclaimLastId();

		// then:
		verify(seqNo).decrement();
	}

	@Test
	void exceptionalSourceAlwaysThrows() {
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::newAccountId);
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::newFileId);
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::newTokenId);
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::newScheduleId);
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::newTopicId);
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::newContractId);
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::reclaimLastId);
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::reclaimProvisionalIds);
		assertThrows(UnsupportedOperationException.class, NOOP_ID_SOURCE::resetProvisionalIds);
	}

	@Test
	void newScheduleId() {
		given(seqNo.getAndIncrement()).willReturn(3L);
		var scheduleId = subject.newScheduleId();
		assertNotNull(scheduleId);
		assertEquals(3, scheduleId.getScheduleNum());
		assertEquals(0, scheduleId.getRealmNum());
		assertEquals(0, scheduleId.getShardNum());
	}

	@Test
	void newContractId() {
		given(seqNo.getAndIncrement()).willReturn(3L);
		var contractId = subject.newContractId();
		assertNotNull(contractId);
		assertEquals(3, contractId.getContractNum());
		assertEquals(0, contractId.getRealmNum());
		assertEquals(0, contractId.getShardNum());
	}
}
