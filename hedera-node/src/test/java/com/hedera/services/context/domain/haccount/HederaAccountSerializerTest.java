package com.hedera.services.context.domain.haccount;

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

import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.swirlds.common.io.FCDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import static com.hedera.services.context.domain.haccount.HederaAccountSerializer.HEDERA_ACCOUNT_SERIALIZER;
import static com.hedera.services.context.domain.haccount.HederaAccountTest.legacyAccount;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.context.domain.haccount.HederaAccountSerializer.*;
import static com.hedera.services.legacy.logic.ApplicationConstants.N;
import static com.hedera.services.legacy.logic.ApplicationConstants.P;

@RunWith(JUnitPlatform.class)
class HederaAccountSerializerTest {
	DomainSerdes serdes;
	FCDataOutputStream out;
	HederaAccountSerializer subject = HEDERA_ACCOUNT_SERIALIZER;

	@BeforeEach
	private void setup() {
		out = mock(FCDataOutputStream.class);

		serdes = mock(DomainSerdes.class);
		subject.serdes = serdes;
	}

	@AfterEach
	private void cleanup() {
		subject.serdes = new DomainSerdes();
	}

	@Test
	public void serializesWithProxyAsExpected() throws Exception {
		InOrder inOrder = inOrder(out, serdes);

		// given:
		HederaAccount account = legacyAccount();

		// when:
		subject.serializeExceptRecords(account, out);

		// then:
		inOrder.verify(out).writeLong(SERIALIZED_VERSION);
		inOrder.verify(out).writeLong(OBJECT_ID);
		inOrder.verify(out).writeLong(account.balance);
		inOrder.verify(out).writeLong(account.senderThreshold);
		inOrder.verify(out).writeLong(account.receiverThreshold);
		inOrder.verify(out).writeByte((byte)(account.receiverSigRequired ? 1 : 0));
		inOrder.verify(serdes).serializeKey(account.accountKeys, out);
		inOrder.verify(out).writeChar(P);
		inOrder.verify(serdes).serializeId(account.proxyAccount, out);
		inOrder.verify(out).writeLong(account.autoRenewPeriod);
		inOrder.verify(out).writeByte((byte)(account.deleted ? 1 : 0));
		inOrder.verify(out).writeLong(account.expirationTime);
		inOrder.verify(out).writeUTF(account.memo);
		inOrder.verify(out).writeByte((byte)(account.isSmartContract ? 1 : 0));
	}

	@Test
	public void serializesWithoutProxyAsExpected() throws Exception {
		// given:
		HederaAccount account = new HederaAccount();

		// when:
		subject.serializeExceptRecords(account, out);

		// then:
		verify(out).writeChar(N);
		verify(out, never()).writeChar(P);
		verify(serdes, never()).serializeId(any(), any());
	}
}
