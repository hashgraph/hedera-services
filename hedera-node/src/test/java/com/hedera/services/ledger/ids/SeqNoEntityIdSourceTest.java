package com.hedera.services.ledger.ids;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;

@RunWith(JUnitPlatform.class)
class SeqNoEntityIdSourceTest {
	final AccountID sponsor = asAccount("1.2.3");
	SequenceNumber seqNo;
	SeqNoEntityIdSource subject;

	@BeforeEach
	private void setup() {
		seqNo = mock(SequenceNumber.class);
		subject = new SeqNoEntityIdSource(() -> seqNo);
	}

	@Test
	public void returnsExpectedAccountId() {
		given(seqNo.getAndIncrement()).willReturn(555L);

		// when:
		AccountID newId = subject.newAccountId(sponsor);

		// then:
		assertEquals(asAccount("1.2.555"), newId);
	}

	@Test
	public void returnsExpectedFileId() {
		given(seqNo.getAndIncrement()).willReturn(555L);

		// when:
		FileID newId = subject.newFileId(sponsor);

		// then:
		assertEquals(asFile("1.2.555"), newId);
	}

	@Test
	public void returnsExpectedTokenId() {
		given(seqNo.getAndIncrement()).willReturn(555L);

		// when:
		TokenID newId = subject.newTokenId(sponsor);

		// then:
		assertEquals(asToken("1.2.555"), newId);
	}

	@Test
	public void reclaimDecrementsId() {
		// when:
		subject.reclaimLastId();

		// then:
		verify(seqNo).decrement();
	}
}
