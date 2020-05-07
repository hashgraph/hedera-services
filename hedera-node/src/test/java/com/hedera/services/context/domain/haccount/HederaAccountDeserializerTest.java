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
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.fcmap.fclist.FCLinkedList;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hedera.services.context.domain.haccount.HederaAccountDeserializer.HEDERA_ACCOUNT_DESERIALIZER;
import static com.hedera.test.utils.IdUtils.asContract;

@RunWith(JUnitPlatform.class)
class HederaAccountDeserializerTest {
	FCDataInputStream in;
	DomainSerdes serdes;
	HederaAccountDeserializer subject = HEDERA_ACCOUNT_DESERIALIZER;

	@BeforeEach
	private void setup() {
		in = mock(FCDataInputStream.class);
		serdes = mock(DomainSerdes.class);

		subject.serdes = serdes;
	}

	@AfterEach
	private void cleanup() {
		subject.serdes = new DomainSerdes();
	}

	@Test
	public void deserializesV5() throws Exception {
		setupV5StubsForDeletedSmartContractWithProxy();

		// given:
		HederaAccount account = subject.deserialize(in);

		// expect:
		assertEquals(balance, account.getBalance());
		assertEquals(memo, account.getMemo());
		assertEquals(proxy, account.getProxyAccount());
		assertEquals(contractKey, account.getAccountKeys());
		assertEquals(sendThreshold, account.getSenderThreshold());
		assertEquals(autoRenewPeriod, account.getAutoRenewPeriod());
		assertEquals(receiveThreshold, account.getReceiverThreshold());
		assertEquals(isReceiverSigRequired, account.isReceiverSigRequired());
		assertEquals(2, account.getRecords().size());
		assertTrue(account.isSmartContract());
		assertTrue(account.isDeleted());
		// and:
		verify(in, never()).readFully(any());
		verify(serdes).deserializeIntoRecords(argThat(in::equals), any());
	}

	@Test
	public void deserializesV4() throws Exception {
		setupV4StubsForDeletedSmartContractWithProxy();

		// given:
		HederaAccount account = subject.deserialize(in);

		// expect:
		assertEquals(balance, account.getBalance());
		assertEquals(memo, account.getMemo());
		assertEquals(proxy, account.getProxyAccount());
		assertEquals(contractKey, account.getAccountKeys());
		assertEquals(sendThreshold, account.getSenderThreshold());
		assertEquals(autoRenewPeriod, account.getAutoRenewPeriod());
		assertEquals(receiveThreshold, account.getReceiverThreshold());
		assertEquals(isReceiverSigRequired, account.isReceiverSigRequired());
		assertEquals(2, account.recordList().size());
		assertTrue(account.isSmartContract());
		assertTrue(account.isDeleted());
		// and:
		verify(in, never()).readFully(any());
		verify(serdes).deserializeIntoLegacyRecords(argThat(in::equals), any());
	}

	@Test
	public void deserializesV3() throws Exception {
		setupV3StubsForDeletedSmartContractWithProxy();

		// given:
		HederaAccount account = subject.deserialize(in);

		// expect:
		assertEquals(balance, account.getBalance());
		assertEquals(proxy, account.getProxyAccount());
		assertEquals(contractKey, account.getAccountKeys());
		assertEquals(sendThreshold, account.getSenderThreshold());
		assertEquals(autoRenewPeriod, account.getAutoRenewPeriod());
		assertEquals(receiveThreshold, account.getReceiverThreshold());
		assertEquals(isReceiverSigRequired, account.isReceiverSigRequired());
		assertTrue(account.isSmartContract());
		assertTrue(account.isDeleted());
		// and:
		verify(in, never()).readFully(any());
		verify(serdes).deserializeIntoLegacyRecords(argThat(in::equals), any());
	}

	@Test
	public void deserializesV2() throws Exception {
		setupV2StubsForDeletedSmartContractWithProxy();

		// given:
		HederaAccount account = subject.deserialize(in);

		// expect:
		assertEquals(balance, account.getBalance());
		assertEquals(proxy, account.getProxyAccount());
		assertEquals(contractKey, account.getAccountKeys());
		assertEquals(sendThreshold, account.getSenderThreshold());
		assertEquals(autoRenewPeriod, account.getAutoRenewPeriod());
		assertEquals(receiveThreshold, account.getReceiverThreshold());
		assertEquals(isReceiverSigRequired, account.isReceiverSigRequired());
		assertTrue(account.isSmartContract());
		assertTrue(account.isDeleted());
		// and:
		verify(in, never()).readFully(any());
	}

	@Test
	public void deserializesV1() throws Exception {
		setupV1StubsForSmartContract();

		// given:
		HederaAccount account = subject.deserialize(in);

		// expect:
		assertEquals(balance, account.getBalance());
		assertEquals(contractKey, account.getAccountKeys());
		assertEquals(sendThreshold, account.getSenderThreshold());
		assertEquals(receiveThreshold, account.getReceiverThreshold());
		assertEquals(isReceiverSigRequired, account.isReceiverSigRequired());
		assertTrue(account.isSmartContract());
		// and:
		verify(in).readFully(any());
	}

	private void setupV5StubsForDeletedSmartContractWithProxy() throws Exception {
		given(in.readLong())
				.willReturn(5L)
				.willReturn(objId)
				.willReturn(balance)
				.willReturn(sendThreshold)
				.willReturn(receiveThreshold)
				.willReturn(autoRenewPeriod)
				.willReturn(expiry);
		given(in.readChar())
				.willReturn(ApplicationConstants.P);
		given(in.readUTF())
				.willReturn(memo);
		given(in.readByte())
				.willReturn((byte)(isReceiverSigRequired ? 1 : 0))
				.willReturn((byte)1)
				.willReturn((byte)(isSmartContract ? 1 : 0));
		given(serdes.deserializeKey(in)).willReturn(contractKey);
		given(serdes.deserializeId(in)).willReturn(proxy);
		will(invoke -> {
			@SuppressWarnings("unchecked")
			FCQueue<JTransactionRecord> records = invoke.getArgument(1);
			records.offer(new JTransactionRecord());
			records.offer(new JTransactionRecord());
			return null;
		}).given(serdes).deserializeIntoRecords(argThat(in::equals), any());
	}

	private void setupV4StubsForDeletedSmartContractWithProxy() throws Exception {
		given(in.readLong())
				.willReturn(4L)
				.willReturn(objId)
				.willReturn(balance)
				.willReturn(sendThreshold)
				.willReturn(receiveThreshold)
				.willReturn(autoRenewPeriod)
				.willReturn(expiry);
		given(in.readChar())
				.willReturn(ApplicationConstants.P);
		given(in.readUTF())
				.willReturn(memo);
		given(in.readByte())
				.willReturn((byte)(isReceiverSigRequired ? 1 : 0))
				.willReturn((byte)1)
				.willReturn((byte)(isSmartContract ? 1 : 0));
		given(serdes.deserializeKey(in)).willReturn(contractKey);
		given(serdes.deserializeId(in)).willReturn(proxy);
		will(invoke -> {
			@SuppressWarnings("unchecked")
			FCLinkedList<JTransactionRecord> records = invoke.getArgument(1);
			records.add(new JTransactionRecord());
			records.add(new JTransactionRecord());
			return null;
		}).given(serdes).deserializeIntoLegacyRecords(argThat(in::equals), any());
	}

	private void setupV3StubsForDeletedSmartContractWithProxy() throws Exception {
		given(in.readLong())
				.willReturn(3L)
				.willReturn(objId)
				.willReturn(balance)
				.willReturn(sendThreshold)
				.willReturn(receiveThreshold)
				.willReturn(autoRenewPeriod)
				.willReturn(expiry);
		given(in.readChar())
				.willReturn(ApplicationConstants.P);
		given(in.readByte())
				.willReturn((byte)(isReceiverSigRequired ? 1 : 0))
				.willReturn((byte)1);
		given(serdes.deserializeKey(in)).willReturn(contractKey);
		given(serdes.deserializeId(in)).willReturn(proxy);
	}

	private void setupV2StubsForDeletedSmartContractWithProxy() throws Exception {
		given(in.readLong())
				.willReturn(2L)
				.willReturn(objId)
				.willReturn(balance)
				.willReturn(sendThreshold)
				.willReturn(receiveThreshold)
				.willReturn(autoRenewPeriod);
		given(in.readChar())
				.willReturn((char)(isReceiverSigRequired ? 1 : 0))
				.willReturn(ApplicationConstants.P)
				.willReturn((char)1);
		given(serdes.deserializeKey(in)).willReturn(contractKey);
		given(serdes.deserializeId(in)).willReturn(proxy);
	}

	private void setupV1StubsForSmartContract() throws Exception {
		given(in.readLong())
				.willReturn(1L)
				.willReturn(objId)
				.willReturn(balance)
				.willReturn(sendThreshold)
				.willReturn(receiveThreshold);
		given(in.readChar()).willReturn((char)(isReceiverSigRequired ? 1 : 0));
		given(serdes.deserializeKey(in)).willReturn(contractKey);
	}

	private long objId = -1;
	private long expiry = 9_999_999L;
	private long balance = 1_234_567L;
	private long sendThreshold = 555L;
	private long receiveThreshold = 666L;
	private long autoRenewPeriod = 5_432L;
	private String memo = "This was Mr. Bleaney's room...";
	private boolean isReceiverSigRequired = true;
	private boolean isSmartContract = true;
	private JKey contractKey = uncheckedDecode(Key.newBuilder().setContractID(asContract("1.2.3")).build());
	private JAccountID proxy = new JAccountID(3, 2, 1);

	private JKey uncheckedDecode(Key key) {
		try {
			return JKey.mapKey(key);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
